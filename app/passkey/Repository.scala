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
    val userIdentity = toUserIdentity(userId)
    val key = Map(
      "username" -> AttributeValue.fromS(userIdentity.username),
      "credentialId" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(passkeyId)
      )
    )
    val request = GetItemRequest
      .builder()
      .tableName(tableName)
      .key(key.asJava)
      .build()

    dynamoDb
      .getItem(request)
      .asScala
      .flatMap { response =>
        if (response.hasItem) {
          Future.fromTry(
            extractCredential(response.item().asScala.toMap, userIdentity)
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
          JanusException.failedToLoadDbItem(userIdentity, tableName, err)
        )
      }
  }

  override def loadPasskeyIds(userId: String): Future[List[String]] = {
    val userIdentity = toUserIdentity(userId)
    val expressionValues = Map(
      ":username" -> AttributeValue.fromS(userIdentity.username)
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
          JanusException.failedToLoadDbItem(userIdentity, tableName, err)
        )
      }
  }

  override def insertPasskey(
      userId: String,
      passkeyName: String,
      credentialRecord: CredentialRecord
  ): Future[Unit] = {
    val userIdentity = toUserIdentity(userId)
    val registrationTime = Instant.now()
    val item = Map(
      "username" -> AttributeValue.fromS(userIdentity.username),
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
          JanusException.failedToCreateDbItem(userIdentity, tableName, err)
        )
      }
  }

  override def updateAuthenticationCount(
      userId: String,
      credentialId: Array[Byte],
      signCount: Long
  ): Future[Unit] = {
    updateAttribute(
      toUserIdentity(userId),
      credentialId,
      "authCounter",
      AttributeValue.fromN(String.valueOf(signCount))
    )
  }

  override def updateLastUsedTime(
      userId: String,
      passkeyId: Array[Byte],
      timestamp: Instant
  ): Future[Unit] = {
    updateAttribute(
      toUserIdentity(userId),
      passkeyId,
      "lastUsedTime",
      AttributeValue.fromS(timestamp.toString)
    )
  }

  def deletePasskey(
      userId: String,
      passkeyId: String
  ): Future[String] = {
    val userIdentity = toUserIdentity(userId)
    val key = Map(
      "username" -> AttributeValue.fromS(userIdentity.username),
      "credentialId" -> AttributeValue.fromS(passkeyId)
    )
    val request = DeleteItemRequest
      .builder()
      .tableName(tableName)
      .key(key.asJava)
      .build()

    dynamoDb
      .deleteItem(request)
      .asScala
      .map(_ => "TODOPasskeyname")
      .recoverWith { case err =>
        Future.failed(
          JanusException.failedToDeleteDbItem(userIdentity, tableName, err)
        )
      }
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

  private def updateAttribute(
      user: UserIdentity,
      credentialId: Array[Byte],
      attribName: String,
      attribValue: AttributeValue
  ): Future[Unit] = {
    val key = Map(
      "username" -> AttributeValue.fromS(user.username),
      "credentialId" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(credentialId)
      )
    )
    val update = Map(s":${attribName}Value" -> attribValue)
    val request = UpdateItemRequest.builder
      .tableName(tableName)
      .key(key.asJava)
      .updateExpression(s"SET $attribName = :${attribName}Value")
      .expressionAttributeValues(update.asJava)
      .build()

    dynamoDb
      .updateItem(request)
      .asScala
      .map(_ => ())
      .recoverWith { case err =>
        Future.failed(
          JanusException.failedToUpdateDbItem(user, tableName, attribName, err)
        )
      }
  }

  private def extractCredential(
      item: Map[String, AttributeValue],
      user: UserIdentity
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
  }.adaptError(err => JanusException.failedToLoadDbItem(user, tableName, err))
}
