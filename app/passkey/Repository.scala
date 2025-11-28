package passkey

import cats.implicits.catsSyntaxMonadError
import com.gu.googleauth.UserIdentity
import com.gu.playpasskeyauth.services.PasskeyRepository
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.{CredentialRecord, CredentialRecordImpl}
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.extension.authenticator.{
  AuthenticationExtensionsAuthenticatorOutputs,
  RegistrationExtensionAuthenticatorOutput
}
import com.webauthn4j.util.Base64UrlUtil
import models.JanusException
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue,
  DeleteItemRequest,
  GetItemRequest,
  PutItemRequest,
  QueryRequest,
  UpdateItemRequest
}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import scala.util.Try

class Repository(dynamoDb: DynamoDbAsyncClient)(using ExecutionContext)
    extends PasskeyRepository {

  private val tableName = "Passkeys"

  private val objConverter = new ObjectConverter()

  private val credentialDataConverter = new AttestedCredentialDataConverter(
    objConverter
  )

  override def loadPasskey(
      userId: String,
      passkeyId: Array[Byte]
  ): Future[CredentialRecord] = {
    val request = GetItemRequest
      .builder()
      .tableName(tableName)
      .key(toKey(userId, passkeyId).asJava)
      .build()

    dynamoDb
      .getItem(request)
      .asScala
      .flatMap { response =>
        if (response.hasItem) {
          Future.fromTry(
            extractCredential(userId, response.item().asScala.toMap)
          )
        } else {
          Future.failed(
            RuntimeException(
              s"Failed to load credential record for user: $userId"
            )
          )
        }
      }
      .recoverWith { case err =>
        Future.failed(
          JanusException.failedToLoadDbItem(userId, tableName, err)
        )
      }
  }

  override def loadPasskeyIds(userName: String): Future[List[String]] = {
    val expressionValues = Map(
      ":username" -> AttributeValue.fromS(userName)
    )
    val request = QueryRequest
      .builder()
      .tableName(tableName)
      .keyConditionExpression("username = :username")
      .expressionAttributeValues(expressionValues.asJava)
      .build()

    dynamoDb
      .query(request)
      .asScala
      .map { response =>
        if (response.hasItems && !response.items().isEmpty) {
          response.items().asScala.map(_.get("credentialId").s()).toList
        } else {
          List.empty
        }
      }
      .recoverWith { case err =>
        Future.failed(
          JanusException.failedToLoadDbItem(userName, tableName, err)
        )
      }
  }

  override def insertPasskey(
      userName: String,
      passkeyName: String,
      credentialRecord: CredentialRecord
  ): Future[Unit] = {
    val registrationTime = Instant.now()
    val item = Map(
      "username" -> AttributeValue.fromS(userName),
      "credentialId" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          credentialRecord.getAttestedCredentialData.getCredentialId
        )
      ),
      "credential" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          credentialDataConverter.convert(
            credentialRecord.getAttestedCredentialData
          )
        )
      ),
      "attestationStatement" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          objConverter.getCborConverter.writeValueAsBytes(
            credentialRecord.getAttestationStatement
          )
        )
      ),
      "authenticatorExtensions" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          objConverter.getCborConverter.writeValueAsBytes(
            credentialRecord.getAuthenticatorExtensions
          )
        )
      ),
      "authCounter" -> AttributeValue.fromN(
        credentialRecord.getCounter.toString
      ),
      "passkeyName" -> AttributeValue.fromS(passkeyName),
      "registrationTime" -> AttributeValue.fromS(registrationTime.toString),
      "aaguid" -> AttributeValue.fromS(
        credentialRecord.getAttestedCredentialData.getAaguid.toString
      )
    )
    val request = PutItemRequest
      .builder()
      .tableName(tableName)
      .item(item.asJava)
      .build()

    dynamoDb
      .putItem(request)
      .asScala
      .map(_ => ())
      .recoverWith { case err =>
        Future.failed(
          JanusException.failedToCreateDbItem(userName, tableName, err)
        )
      }
  }

  override def updateAuthenticationCount(
      userName: String,
      passkeyId: Array[Byte],
      signCount: Long
  ): Future[Unit] = {
    val update = Map(
      ":authCounterValue" -> AttributeValue.fromN(String.valueOf(signCount))
    )
    val request = UpdateItemRequest.builder
      .tableName(tableName)
      .key(toKey(userName, passkeyId).asJava)
      .updateExpression("SET authCounter = :authCounterValue")
      .expressionAttributeValues(update.asJava)
      .build()

    dynamoDb
      .updateItem(request)
      .asScala
      .map(_ => ())
      .recoverWith { case err =>
        Future.failed(
          JanusException
            .failedToUpdateDbItem(userName, tableName, "authCounter", err)
        )
      }
  }

  override def updateLastUsedTime(
      userName: String,
      passkeyId: Array[Byte],
      timestamp: Instant
  ): Future[Unit] = {
    val update = Map(
      ":lastUsedTimeValue" -> AttributeValue.fromS(timestamp.toString)
    )
    val request = UpdateItemRequest.builder
      .tableName(tableName)
      .key(toKey(userName, passkeyId).asJava)
      .updateExpression("SET lastUsedTime = :lastUsedTimeValue")
      .expressionAttributeValues(update.asJava)
      .build()

    dynamoDb
      .updateItem(request)
      .asScala
      .map(_ => ())
      .recoverWith { case err =>
        Future.failed(
          JanusException
            .failedToUpdateDbItem(userName, tableName, "lastUsedTime", err)
        )
      }
  }

  def deletePasskey(
      userName: String,
      passkeyId: Array[Byte]
  ): Future[String] = {
    val request = DeleteItemRequest
      .builder()
      .tableName(tableName)
      .key(toKey(userName, passkeyId).asJava)
      .build()

    dynamoDb
      .deleteItem(request)
      .asScala
      .map(_ => "TODOPasskeyname")
      .recoverWith { case err =>
        Future.failed(
          JanusException.failedToDeleteDbItem(userName, tableName, err)
        )
      }
  }

  private def toKey(
      userId: String,
      passkeyId: Array[Byte]
  ): Map[String, AttributeValue] = {
    val user = toUserIdentity(userId)
    Map(
      "username" -> AttributeValue.fromS(user.username),
      "credentialId" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(passkeyId)
      )
    )
  }

  private def toUserIdentity(userId: String) =
    UserIdentity(
      sub = "",
      email = userId,
      firstName = "",
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )

  private def extractCredential(
      userName: String,
      item: Map[String, AttributeValue]
  ): Try[CredentialRecord] = Try {
    val attestationStmt = objConverter.getCborConverter.readValue(
      Base64UrlUtil.decode(item("attestationStatement").s()),
      classOf[NoneAttestationStatement]
    )
    val counter = item("authCounter").n().toLong
    val credentialData = credentialDataConverter.convert(
      Base64UrlUtil.decode(item("credential").s())
    )
    val authExts = objConverter.getCborConverter.readValue(
      Base64UrlUtil.decode(item("authenticatorExtensions").s()),
      classOf[AuthenticationExtensionsAuthenticatorOutputs[
        RegistrationExtensionAuthenticatorOutput
      ]]
    )
    new CredentialRecordImpl(
      attestationStmt,
      null,
      null,
      null,
      counter,
      credentialData,
      authExts,
      null,
      null,
      null
    )
  }.adaptError(err =>
    JanusException.failedToLoadDbItem(userName, tableName, err)
  )
}
