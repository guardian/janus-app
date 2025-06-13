package aws

import aws.PasskeyChallengeDB.UserChallenge.toDynamoItem
import cats.implicits.catsSyntaxMonadError
import com.gu.googleauth.UserIdentity
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import com.webauthn4j.util.Base64UrlUtil
import models.JanusException
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Try}

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
  )(using dynamoDB: DynamoDbClient): Try[Unit] =
    Try {
      val item = toDynamoItem(userChallenge)
      val request =
        PutItemRequest.builder().tableName(tableName).item(item.asJava).build()
      dynamoDB.putItem(request)
      ()
    }.adaptError(err =>
      JanusException.failedToCreateDbItem(userChallenge.user, tableName, err)
    )

  def loadChallenge(
      user: UserIdentity
  )(using dynamoDB: DynamoDbClient): Try[GetItemResponse] = {
    Try {
      val key = Map("username" -> AttributeValue.fromS(user.username))
      val request =
        GetItemRequest.builder().tableName(tableName).key(key.asJava).build()
      dynamoDB.getItem(request)
    }.adaptError(err => JanusException.failedToLoadDbItem(user, tableName, err))
  }

  def extractChallenge(
      response: GetItemResponse,
      user: UserIdentity
  ): Try[Challenge] = {
    if (response.hasItem) {
      Try {
        val item = response.item()
        val challenge =
          Base64UrlUtil.decode(item.get("challenge").s().getBytes(UTF_8))
        new DefaultChallenge(challenge)
      }
    } else
      Failure(JanusException.missingItemInDb(user, tableName))
  }

  def delete(
      user: UserIdentity
  )(using dynamoDB: DynamoDbClient): Try[Unit] =
    Try {
      val key = Map("username" -> AttributeValue.fromS(user.username))
      val request =
        DeleteItemRequest.builder().tableName(tableName).key(key.asJava).build()
      dynamoDB.deleteItem(request)
      ()
    }.adaptError(err =>
      JanusException.failedToDeleteDbItem(user, tableName, err)
    )
}
