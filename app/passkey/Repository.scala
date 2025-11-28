package passkey

import aws.PasskeyDB
import cats.implicits.catsSyntaxMonadError
import com.gu.googleauth.UserIdentity
import com.gu.playpasskeyauth.services.PasskeyRepository
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.util.Base64UrlUtil
import models.JanusException
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue,
  UpdateItemRequest
}

import java.time.Instant
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Try}

// TODO simplify
class Repository(using DynamoDbClient) extends PasskeyRepository {

  override def loadPasskey(
      userId: String,
      passkeyId: Array[Byte]
  ): Future[CredentialRecord] = {
    val userIdentity = toUserIdentity(userId)
    Future.fromTry(
      PasskeyDB.loadCredential(userIdentity, passkeyId).flatMap { response =>
        if (response.hasItem) {
          PasskeyDB.extractCredential(response, userIdentity)
        } else
          Failure(
            RuntimeException(
              s"Failed to load credential record for user: $userId"
            )
          )
      }
    )
  }

  override def loadPasskeyIds(userId: String): Future[List[String]] = {
    Future.fromTry(
      PasskeyDB.loadCredentials(toUserIdentity(userId)).map { response =>
        PasskeyDB.extractMetadata(response).map(_.id).toList
      }
    )
  }

  override def insertPasskey(
      userId: String,
      passkeyName: String,
      credentialRecord: CredentialRecord
  ): Future[Unit] = {
    Future.fromTry(
      PasskeyDB.insert(toUserIdentity(userId), credentialRecord, passkeyName)
    )
  }

  override def updateAuthenticationCount(
      userId: String,
      credentialId: Array[Byte],
      signCount: Long
  ): Future[Unit] = {
    Future.fromTry(
      updateAttribute(
        toUserIdentity(userId),
        credentialId,
        "authCounter",
        AttributeValue.fromN(
          String.valueOf(signCount)
        )
      )
    )
  }

  override def updateLastUsedTime(
      userId: String,
      passkeyId: Array[Byte],
      timestamp: Instant
  ): Future[Unit] = {
    Future.fromTry(
      updateAttribute(
        toUserIdentity(userId),
        passkeyId,
        "lastUsedTime",
        AttributeValue.fromS(timestamp.toString)
      )
    )
  }

  def deletePasskey(
      userId: String,
      passkeyId: String
  ): Future[String] =
    Future.fromTry(
      PasskeyDB
        .deleteById(toUserIdentity(userId), passkeyId)
        .map(_ => "TODOPasskeyname")
    )

  private def toUserIdentity(userId: String) =
    UserIdentity(
      sub = "",
      email = userId,
      firstName = "",
      lastName = "",
      exp = 0L,
      avatarUrl = None
    )

  private val tableName = "Passkeys"

  private def updateAttribute(
      user: UserIdentity,
      credentialId: Array[Byte],
      attribName: String,
      attribValue: AttributeValue
  )(using
      dynamoDB: DynamoDbClient
  ): Try[Unit] = Try {
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
    dynamoDB.updateItem(request)
    ()
  }.adaptError(err =>
    JanusException.failedToUpdateDbItem(user, tableName, attribName, err)
  )
}
