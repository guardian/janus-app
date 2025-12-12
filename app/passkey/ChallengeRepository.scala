package passkey

import com.gu.playpasskeyauth.services.PasskeyChallengeRepository
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import com.webauthn4j.util.Base64UrlUtil
import models.PasskeyFlow
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.{
  AttributeValue,
  DeleteItemRequest,
  GetItemRequest,
  PutItemRequest
}

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*

class ChallengeRepository(dynamoDb: DynamoDbAsyncClient)(using ExecutionContext)
    extends PasskeyChallengeRepository {

  private val tableName = "PasskeyChallenges"

  def loadRegistrationChallenge(userId: String): Future[Challenge] =
    loadChallenge(userId, PasskeyFlow.Registration)

  override def loadAuthenticationChallenge(userId: String): Future[Challenge] =
    loadChallenge(userId, PasskeyFlow.Authentication)

  def insertRegistrationChallenge(
      userId: String,
      challenge: Challenge
  ): Future[Unit] =
    insertChallenge(userId, PasskeyFlow.Registration, challenge)

  def insertAuthenticationChallenge(
      userId: String,
      challenge: Challenge
  ): Future[Unit] =
    insertChallenge(userId, PasskeyFlow.Authentication, challenge)

  def deleteRegistrationChallenge(userId: String): Future[Unit] =
    deleteChallenge(userId, PasskeyFlow.Registration)

  override def deleteAuthenticationChallenge(userId: String): Future[Unit] =
    deleteChallenge(userId, PasskeyFlow.Authentication)

  private def loadChallenge(
      userId: String,
      flow: PasskeyFlow
  ): Future[Challenge] = {
    val request = GetItemRequest
      .builder()
      .tableName(tableName)
      .key(toKey(userId, flow).asJava)
      .build()

    dynamoDb
      .getItem(request)
      .asScala
      .flatMap { response =>
        if (response.hasItem) {
          val challengeBytes =
            Base64UrlUtil.decode(
              response.item().get("challenge").s().getBytes(UTF_8)
            )
          Future.successful(new DefaultChallenge(challengeBytes))
        } else {
          Future.failed(
            RuntimeException(
              s"Failed to load ${flow.toString.toLowerCase} challenge for user: $userId"
            )
          )
        }
      }
  }

  private def insertChallenge(
      userId: String,
      flow: PasskeyFlow,
      challenge: Challenge
  ): Future[Unit] = {
    val expiresAt = Instant.now().plusSeconds(60)
    val item = Map(
      "username" -> AttributeValue.fromS(userId),
      "flow" -> AttributeValue.fromS(flow.toString),
      "challenge" -> AttributeValue.fromS(
        Base64UrlUtil.encodeToString(challenge.getValue)
      ),
      "expiresAt" -> AttributeValue.fromN(expiresAt.getEpochSecond.toString)
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
  }

  private def deleteChallenge(
      userId: String,
      flow: PasskeyFlow
  ): Future[Unit] = {
    val request = DeleteItemRequest
      .builder()
      .tableName(tableName)
      .key(toKey(userId, flow).asJava)
      .build()

    dynamoDb
      .deleteItem(request)
      .asScala
      .map(_ => ())
  }

  private def toKey(
      userId: String,
      flow: PasskeyFlow
  ): Map[String, AttributeValue] =
    Map(
      "username" -> AttributeValue.fromS(userId),
      "flow" -> AttributeValue.fromS(flow.toString)
    )
}
