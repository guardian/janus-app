package services.passkeyauth

import aws.PasskeyDB
import com.gu.playpasskeyauth.models.{
  Passkey as LibPasskey,
  PasskeyId,
  PasskeyName,
  UserId
}
import com.gu.playpasskeyauth.services.{
  PasskeyError,
  PasskeyException,
  PasskeyRepository
}
import com.webauthn4j.data.attestation.authenticator.AAGUID
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue,
  GetItemResponse,
  PutItemRequest
}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try}

class DynamoPasskeyRepository(using
    dynamoDb: DynamoDbClient,
    ec: ExecutionContext
) extends PasskeyRepository {

  import UserIdentityConversions.toUserIdentity

  private val tableName = "Passkeys"

  override def get(userId: UserId, passkeyId: PasskeyId): Future[LibPasskey] =
    Future.fromTry {
      val user = toUserIdentity(userId)
      for {
        response <- PasskeyDB.loadCredential(user, passkeyId.bytes)
        _ <-
          if response.hasItem then Success(())
          else Failure(PasskeyException(PasskeyError.PasskeyNotFound))
        passkey <- itemToPasskey(userId, response)
      } yield passkey
    }

  override def list(userId: UserId): Future[List[LibPasskey]] =
    Future.fromTry {
      val user = toUserIdentity(userId)
      for {
        response <- PasskeyDB.loadCredentials(user)
        passkeys <- response
          .items()
          .asScala
          .map(item =>
            itemToPasskey(userId, GetItemResponse.builder().item(item).build())
          )
          .foldLeft(Try(List.empty[LibPasskey])) { (acc, next) =>
            for {
              existing <- acc
              passkey <- next
            } yield passkey :: existing
          }
          .map(_.reverse)
      } yield passkeys
    }

  override def upsert(userId: UserId, passkey: LibPasskey): Future[Unit] =
    Future.fromTry {
      val user = toUserIdentity(userId)
      val baseItem = PasskeyDB.toDynamoItem(
        user,
        passkey.credentialRecord,
        passkey.name.value,
        passkey.createdAt
      )
      val itemWithCounter =
        baseItem.updated(
          "authCounter",
          AttributeValue.fromN(passkey.signCount.toString)
        )
      val finalItem = passkey.lastUsedAt
        .map(lastUsed =>
          itemWithCounter.updated(
            "lastUsedTime",
            AttributeValue.fromS(lastUsed.toString)
          )
        )
        .getOrElse(itemWithCounter)

      Try {
        val request =
          PutItemRequest
            .builder()
            .tableName(tableName)
            .item(finalItem.asJava)
            .build()
        dynamoDb.putItem(request)
        ()
      }
    }

  override def delete(userId: UserId, passkeyId: PasskeyId): Future[Unit] =
    Future.fromTry {
      PasskeyDB.deleteById(toUserIdentity(userId), passkeyId.toBase64Url)
    }

  private def itemToPasskey(
      userId: UserId,
      response: GetItemResponse
  ): Try[LibPasskey] =
    if response.hasItem then {
      val item = response.item()
      val user = toUserIdentity(userId)
      for {
        credential <- PasskeyDB.extractCredential(response, user)
        passkeyName <- Try(PasskeyName(item.get("passkeyName").s()))
        registrationTime <- Try(Instant.parse(item.get("registrationTime").s()))
        aaguid <- Try(new AAGUID(item.get("aaguid").s()))
      } yield LibPasskey(
        id = PasskeyId.fromBase64Url(item.get("credentialId").s()),
        name = passkeyName,
        credentialRecord = credential,
        createdAt = registrationTime,
        lastUsedAt =
          Option(item.get("lastUsedTime")).map(v => Instant.parse(v.s())),
        signCount = item.get("authCounter").n().toLong,
        aaguid = aaguid
      )
    } else Failure(PasskeyException(PasskeyError.PasskeyNotFound))

}
