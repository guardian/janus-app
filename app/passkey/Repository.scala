package passkey

import cats.implicits.catsSyntaxMonadError
import com.gu.googleauth.UserIdentity
import com.gu.playpasskeyauth.models.{
  PasskeyId,
  PasskeyInfo,
  PasskeyName,
  UserId
}
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
import software.amazon.awssdk.services.dynamodb.model.*

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
      userId: UserId,
      passkeyId: PasskeyId
  ): Future[CredentialRecord] =
    loadPasskeyRecord(userId, passkeyId).flatMap(item =>
      Future.fromTry(extractCredential(userId, item))
    )

  override def loadPasskeyInfo(
      userId: UserId,
      passkeyId: PasskeyId
  ): Future[PasskeyInfo] =
    loadPasskeyRecord(userId, passkeyId).map(item =>
      PasskeyInfo(
        id = PasskeyId.fromBase64Url(item("credentialId").s()),
        name = PasskeyName(item("passkeyName").s()),
        createdAt = Instant.parse(item("registrationTime").s()),
        lastUsedAt =
          item.get("lastUsedTime").map(attrib => Instant.parse(attrib.s()))
      )
    )

  override def loadPasskeyIds(userId: UserId): Future[List[PasskeyId]] =
    loadPasskeyRecords(userId).map(
      _.flatMap(
        _.get("credentialId").map(attrib => PasskeyId.fromBase64Url(attrib.s()))
      )
    )

  override def loadPasskeyNames(userId: UserId): Future[List[String]] =
    loadPasskeyRecords(userId).map(_.flatMap(_.get("passkeyName").map(_.s())))

  override def listPasskeys(userId: UserId): Future[List[PasskeyInfo]] =
    loadPasskeyRecords(userId).map(
      _.flatMap(attribs =>
        for {
          idAttrib <- attribs.get("credentialId")
          nameAttrib <- attribs.get("passkeyName")
          name <- PasskeyName.validate(nameAttrib.s()).toOption
          createdAtAttrib <- attribs.get("registrationTime")
          lastUsedAtAttrib = attribs.get("lastUsedTime")
        } yield PasskeyInfo(
          id = PasskeyId.fromBase64Url(idAttrib.s()),
          name,
          createdAt = Instant.parse(createdAtAttrib.s()),
          lastUsedAt = lastUsedAtAttrib.map(attrib => Instant.parse(attrib.s()))
        )
      )
    )

  override def insertPasskey(
      userId: UserId,
      passkeyName: String,
      credentialRecord: CredentialRecord
  ): Future[Unit] = {
    val registrationTime = Instant.now()
    val item = Map(
      "username" -> AttributeValue.fromS(userId.value),
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
          objConverter.getCborMapper.writeValueAsBytes(
            credentialRecord.getAttestationStatement
          )
        )
      ),
      "authenticatorExtensions" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          objConverter.getCborMapper.writeValueAsBytes(
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
          JanusException
            .failedToCreateDbItem(toUserIdentity(userId), tableName, err)
        )
      }
  }

  override def updateAuthenticationCount(
      userId: UserId,
      passkeyId: PasskeyId,
      signCount: Long
  ): Future[Unit] = {
    val update = Map(
      ":authCounterValue" -> AttributeValue.fromN(String.valueOf(signCount))
    )
    val request = UpdateItemRequest.builder
      .tableName(tableName)
      .key(toKey(userId, passkeyId).asJava)
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
            .failedToUpdateDbItem(
              toUserIdentity(userId),
              tableName,
              "authCounter",
              err
            )
        )
      }
  }

  override def updateLastUsedTime(
      userId: UserId,
      passkeyId: PasskeyId,
      timestamp: Instant
  ): Future[Unit] = {
    val update = Map(
      ":lastUsedTimeValue" -> AttributeValue.fromS(timestamp.toString)
    )
    val request = UpdateItemRequest.builder
      .tableName(tableName)
      .key(toKey(userId, passkeyId).asJava)
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
            .failedToUpdateDbItem(
              toUserIdentity(userId),
              tableName,
              "lastUsedTime",
              err
            )
        )
      }
  }

  def deletePasskey(
      userId: UserId,
      passkeyId: PasskeyId
  ): Future[Unit] = {
    val request = DeleteItemRequest
      .builder()
      .tableName(tableName)
      .key(toKey(userId, passkeyId).asJava)
      .build()

    dynamoDb
      .deleteItem(request)
      .asScala
      .map(_ => ())
      .recoverWith { case err =>
        Future.failed(
          JanusException
            .failedToDeleteDbItem(toUserIdentity(userId), tableName, err)
        )
      }
  }

  private def loadPasskeyRecord(
      userId: UserId,
      passkeyId: PasskeyId
  ): Future[Map[String, AttributeValue]] = {
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
          Future.successful(response.item().asScala.toMap)
        } else {
          Future.failed(
            RuntimeException(
              s"Failed to load credential record for user: $userId: $passkeyId"
            )
          )
        }
      }
      .recoverWith { case err =>
        Future.failed(
          JanusException
            .failedToLoadDbItem(toUserIdentity(userId), tableName, err)
        )
      }
  }

  private def loadPasskeyRecords(
      userId: UserId
  ): Future[List[Map[String, AttributeValue]]] = {
    val expressionValues = Map(
      ":username" -> AttributeValue.fromS(userId.value)
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
          response
            .items()
            .asScala
            .map(_.asScala.toMap)
            .toList
        } else {
          List.empty
        }
      }
      .recoverWith { case err =>
        Future.failed(
          JanusException
            .failedToLoadDbItem(toUserIdentity(userId), tableName, err)
        )
      }
  }

  private def toKey(
      userId: UserId,
      passkeyId: PasskeyId
  ): Map[String, AttributeValue] = {
    val user = toUserIdentity(userId)
    Map(
      "username" -> AttributeValue.fromS(user.username),
      "credentialId" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(passkeyId.bytes)
      )
    )
  }

  private def toUserIdentity(userId: UserId) =
    UserIdentity(
      sub = "",
      email = userId.value,
      firstName = "",
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )

  private def extractCredential(
      userId: UserId,
      item: Map[String, AttributeValue]
  ): Try[CredentialRecord] = Try {
    val attestationStmt = objConverter.getCborMapper.readValue(
      Base64UrlUtil.decode(item("attestationStatement").s()),
      classOf[NoneAttestationStatement]
    )
    val counter = item("authCounter").n().toLong
    val credentialData = credentialDataConverter.convert(
      Base64UrlUtil.decode(item("credential").s())
    )
    val authExts = objConverter.getCborMapper.readValue(
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
    JanusException.failedToLoadDbItem(toUserIdentity(userId), tableName, err)
  )
}
