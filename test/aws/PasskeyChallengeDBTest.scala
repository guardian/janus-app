package aws

import aws.PasskeyChallengeDB.*
import com.gu.googleauth.UserIdentity
import com.webauthn4j.data.client.challenge.DefaultChallenge
import models.PasskeyFlow.{Authentication, Registration}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.KeyType.{HASH, RANGE}
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.S
import software.amazon.awssdk.services.dynamodb.model.*

import java.nio.charset.StandardCharsets.UTF_8
import java.time.ZoneOffset.UTC
import java.time.{Clock, Instant}
import scala.util.{Failure, Success}

/** Integration tests of PasskeyChallengeDB.
  *
  * These tests require a local Dynamo DB service to be available. See
  * [[./local-dev/README.md#setting-up-passkeys-tables Setting up Passkeys tables]]
  */
class PasskeyChallengeDBTest extends AnyFreeSpec with Matchers {

  "test db stuff - use this to test DynamoDB stuff locally during development" - {
    given dynamoDB: DynamoDbClient = Clients.localDb

    "insertion and querying" - {
      val clock = Clock.fixed(Instant.parse("2025-03-31T01:00:00Z"), UTC)

      val userChallenge = UserChallenge(
        UserIdentity(
          sub = "",
          email = "test.user@example.com",
          firstName = "Test",
          lastName = "User",
          exp = 0,
          avatarUrl = None
        ),
        Registration,
        new DefaultChallenge("challenge".getBytes(UTF_8)),
        clock.instant.plusSeconds(60)
      )

      "insertion succeeds" ignore {
        insert(userChallenge) match {
          case Failure(e) =>
            fail(s"Failed to insert user challenge: ${e.getMessage}")
          case Success(_) => succeed
        }
      }

      "load succeeds" ignore {
        (for {
          response <- loadChallenge(userChallenge.user, Authentication)
          challenge <- extractChallenge(response, userChallenge.user)
        } yield challenge) match {
          case Failure(e) =>
            fail(s"Failed to load user challenge: ${e.getMessage}")
          case Success(challenge) =>
            challenge.getValue shouldBe userChallenge.challenge.getValue
        }
      }

      "deletion succeeds" ignore {
        delete(userChallenge.user, Registration) match {
          case Failure(e) =>
            fail(s"Failed to delete user challenge: ${e.getMessage}")
          case Success(_) => succeed
        }
      }
    }

  }
}
