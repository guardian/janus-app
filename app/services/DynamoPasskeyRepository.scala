package services

import com.gu.googleauth.UserIdentity
import com.gu.playpasskeyauth.models.{Passkey, PasskeyId, PasskeyName, UserId}
import com.gu.playpasskeyauth.services.PasskeyRepository
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.extension.authenticator.{
  AuthenticationExtensionsAuthenticatorOutputs,
  RegistrationExtensionAuthenticatorOutput
}
import com.webauthn4j.util.Base64UrlUtil
import models.JanusException
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*

import java.time.{Clock, Instant}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*

class DynamoPasskeyRepository(dynamoDb: DynamoDbAsyncClient, clock: Clock)(using
    ExecutionContext
) extends PasskeyRepository {

  override def get(userId: UserId, passkeyId: PasskeyId): Future[Passkey] =
    loadPasskeyItem(userId, passkeyId).map(item => toPasskey(item, userId))

  override def list(userId: UserId): Future[List[Passkey]] =
    loadPasskeyItems(userId).map(_.map(item => toPasskey(item, userId)))

  override def upsert(userId: UserId, passkey: Passkey): Future[Unit] =
    exists(userId, passkey).flatMap(itemExists =>
      if itemExists then update(userId, passkey)
      else insert(userId, passkey, clock)
    )

  override def delete(userId: UserId, passkeyId: PasskeyId): Future[Unit] = {
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

  // TODO
  private def exists(userId: UserId, passkey: Passkey): Future[Boolean] = ???

  private def insert(
      userId: UserId,
      passkey: Passkey,
      clock: Clock
  ): Future[Unit] = {
    val registrationTime = clock.instant()
    val item = Map(
      "username" -> AttributeValue.fromS(userId.value),
      "credentialId" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          passkey.credentialRecord.getAttestedCredentialData.getCredentialId
        )
      ),
      "credential" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          credentialDataConverter.convert(
            passkey.credentialRecord.getAttestedCredentialData
          )
        )
      ),
      "attestationStatement" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          objConverter.getCborMapper.writeValueAsBytes(
            passkey.credentialRecord.getAttestationStatement
          )
        )
      ),
      "authenticatorExtensions" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(
          objConverter.getCborMapper.writeValueAsBytes(
            passkey.credentialRecord.getAuthenticatorExtensions
          )
        )
      ),
      "authCounter" -> AttributeValue.fromN(
        passkey.credentialRecord.getCounter.toString
      ),
      "passkeyName" -> AttributeValue.fromS(passkey.name.value),
      "registrationTime" -> AttributeValue.fromS(registrationTime.toString),
      "aaguid" -> AttributeValue.fromS(
        passkey.credentialRecord.getAttestedCredentialData.getAaguid.toString
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

  private def update(userId: UserId, passkey: Passkey): Future[Unit] = {
    val request = UpdateItemRequest.builder
      .tableName(tableName)
      .key(toKey(userId, passkey.id).asJava)
      .updateExpression("SET authCounter = :authCounterValue, lastUsedTime = :lastUsedTimeValue")
      .expressionAttributeValues(
        Map(
          ":authCounterValue" -> AttributeValue.fromN(
            String.valueOf(passkey.signCount)
          ),
          ":lastUsedTimeValue" -> AttributeValue.fromS(
            passkey.lastUsedAt.toString
          )
        ).asJava
      )
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

  private val tableName = "Passkeys"

  private val objConverter = new ObjectConverter()

  private val credentialDataConverter = new AttestedCredentialDataConverter(
    objConverter
  )

  private def loadPasskeyItem(
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
              s"Failed to load passkey for user: $userId: $passkeyId"
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

  private def loadPasskeyItems(
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

  private def toPasskey(
      item: Map[String, AttributeValue],
      userId: UserId
  ): Passkey = {
    val credentialId = item("credentialId").s()
    val name = item("passkeyName").s()
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
    val registrationTime = Instant.parse(item("registrationTime").s())
    val lastUsedAt =
      item.get("lastUsedTime").map(attrib => Instant.parse(attrib.s()))
    new Passkey(
      id = PasskeyId.fromBase64Url(credentialId),
      name = PasskeyName(name),
      credentialRecord = new CredentialRecordImpl(
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
      ),
      createdAt = registrationTime,
      lastUsedAt,
      signCount = counter
    )
  }

  private def toKey(
      userId: UserId,
      passkeyId: PasskeyId
  ): Map[String, AttributeValue] =
    Map(
      "username" -> AttributeValue.fromS(userId.value),
      "credentialId" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(passkeyId.bytes)
      )
    )

  private def toUserIdentity(userId: UserId) =
    UserIdentity(
      sub = "",
      email = userId.value,
      firstName = "",
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )
}
