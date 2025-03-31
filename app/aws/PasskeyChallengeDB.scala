package aws

import aws.PasskeyChallengeDB.UserChallenge.toDynamoItem
import com.gu.googleauth.UserIdentity
import com.webauthn4j.data.client.challenge.{Challenge, DefaultChallenge}
import com.webauthn4j.util.Base64UrlUtil
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model._

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import scala.jdk.CollectionConverters._
import scala.util.Try

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
        "userName" -> AttributeValue.fromS(userChallenge.user.username),
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
  )(implicit dynamoDB: DynamoDbClient): Try[Unit] = {
    val item = toDynamoItem(userChallenge).asJava
    val request =
      PutItemRequest.builder().tableName(tableName).item(item).build()
    Try(dynamoDB.putItem(request))
  }

  def load(
      user: UserIdentity
  )(implicit dynamoDB: DynamoDbClient): Try[Option[Challenge]] = {
    val key = Map(
      "userName" -> AttributeValue.fromS(user.username)
    ).asJava

    val request = GetItemRequest
      .builder()
      .tableName(tableName)
      .key(key)
      .build()

    Try {
      val result = dynamoDB.getItem(request)
      if (result.hasItem) {
        val item = result.item()
        val challenge = Base64UrlUtil.decode(
          item.get("challenge").s().getBytes(UTF_8)
        )
        Some(new DefaultChallenge(challenge))
      } else
        Option.empty
    }
  }

  def delete(
      user: UserIdentity
  )(implicit dynamoDB: DynamoDbClient): Try[Unit] = {
    val key = Map(
      "userName" -> AttributeValue.fromS(user.username)
    ).asJava

    val request = DeleteItemRequest
      .builder()
      .tableName(tableName)
      .key(key)
      .build()

    Try(dynamoDB.deleteItem(request))
  }
}
