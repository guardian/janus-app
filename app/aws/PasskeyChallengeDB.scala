package aws

import aws.PasskeyChallengeDB.UserChallenge.toDynamoItem
import com.gu.googleauth.UserIdentity
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import com.webauthn4j.util.Base64UrlUtil
import models.JanusException
import play.api.http.Status.INTERNAL_SERVER_ERROR
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object PasskeyChallengeDB {

  private[aws] val tableName = "PasskeyChallenges"

  case class UserChallenge(
      user: UserIdentity,
      challenge: Challenge,
      // TTL can take up to 48 hours to take effect so this will be a fallback rather than something to rely on
      expiresAt: Instant = Instant.now().plusSeconds(60)
  )

  object UserChallenge {
    def toDynamoItem(
        userChallenge: UserChallenge
    ): Map[String, AttributeValue] = {
      Map(
        "username" -> AttributeValue.fromS(userChallenge.user.username),
        "challenge" -> AttributeValue.fromS(
          Base64UrlUtil.encodeToString(userChallenge.challenge.getValue)
        ),
        "expiresAt" -> AttributeValue.fromN(
          userChallenge.expiresAt.getEpochSecond.toString
        )
      )
    }
  }

  def insert(
      userChallenge: UserChallenge
  )(implicit dynamoDB: DynamoDbClient): Try[Unit] =
    Try {
      val item = toDynamoItem(userChallenge)
      val request =
        PutItemRequest.builder().tableName(tableName).item(item.asJava).build()
      dynamoDB.putItem(request)
      ()
    }.recoverWith(exception =>
      Failure(
        JanusException(
          userMessage = "Failed to store challenge",
          engineerMessage =
            s"Failed to store challenge for user ${userChallenge.user.username}: ${exception.getMessage}",
          httpCode = INTERNAL_SERVER_ERROR,
          causedBy = Some(exception)
        )
      )
    )

  def load(
      user: UserIdentity
  )(implicit dynamoDB: DynamoDbClient): Try[Challenge] =
    Try {
      val key = Map("username" -> AttributeValue.fromS(user.username))
      val request =
        GetItemRequest.builder().tableName(tableName).key(key.asJava).build()
      dynamoDB.getItem(request)
    }.flatMap(response =>
      if (response.hasItem) {
        val item = response.item()
        val challenge =
          Base64UrlUtil.decode(item.get("challenge").s().getBytes(UTF_8))
        Success(new DefaultChallenge(challenge))
      } else {
        Failure(
          JanusException(
            userMessage = "Challenge not found",
            engineerMessage = s"Challenge not found for user ${user.username}",
            httpCode = INTERNAL_SERVER_ERROR,
            causedBy = None
          )
        )
      }
    ).recoverWith(exception =>
      Failure(
        JanusException(
          userMessage = "Failed to load challenge",
          engineerMessage =
            s"Failed to load challenge for user ${user.username}: ${exception.getMessage}",
          httpCode = INTERNAL_SERVER_ERROR,
          causedBy = Some(exception)
        )
      )
    )

  def delete(
      user: UserIdentity
  )(implicit dynamoDB: DynamoDbClient): Try[Unit] =
    Try {
      val key = Map("username" -> AttributeValue.fromS(user.username))
      val request =
        DeleteItemRequest.builder().tableName(tableName).key(key.asJava).build()
      dynamoDB.deleteItem(request)
      ()
    }.recoverWith(exception =>
      Failure(
        JanusException(
          userMessage = "Failed to store challenge",
          engineerMessage =
            s"Failed to delete challenge for user ${user.username}: ${exception.getMessage}",
          httpCode = INTERNAL_SERVER_ERROR,
          causedBy = Some(exception)
        )
      )
    )
}
