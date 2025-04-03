package aws

import com.gu.googleauth.UserIdentity
import com.webauthn4j.data.client.challenge.DefaultChallenge
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.KeyType.HASH
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.S
import software.amazon.awssdk.services.dynamodb.model._

import java.nio.charset.StandardCharsets.UTF_8
import java.time.ZoneOffset.UTC
import java.time.{Clock, Instant}

class PasskeyChallengeDBTest extends AnyFreeSpec with Matchers {

  "test db stuff - use this to test DynamoDB stuff locally during development" - {
    implicit val dynamoDB: DynamoDbClient = Clients.localDb

    "insertion and querying" - {
      val clock = Clock.fixed(Instant.parse("2025-03-31T01:00:00Z"), UTC)

      val userChallenge = PasskeyChallengeDB.UserChallenge(
        UserIdentity(
          sub = "",
          email = "test.user@example.com",
          firstName = "Test",
          lastName = "User",
          exp = 0,
          avatarUrl = None
        ),
        new DefaultChallenge("challenge".getBytes(UTF_8)),
        clock.instant.plusSeconds(60)
      )

      "insertion succeeds" ignore {
        PasskeyChallengeDB.insert(userChallenge) match {
          case Left(e) =>
            fail(s"Failed to insert user challenge: ${e.getMessage}")
          case Right(_) => succeed
        }
      }

      "load succeeds" ignore {
        PasskeyChallengeDB.load(userChallenge.user) match {
          case Left(e) =>
            fail(s"Failed to load user challenge: ${e.getMessage}")
          case Right(challenge) =>
            challenge.getValue shouldBe userChallenge.challenge.getValue
        }
      }

      "deletion succeeds" ignore {
        PasskeyChallengeDB.delete(userChallenge.user) match {
          case Left(e) =>
            fail(s"Failed to delete user challenge: ${e.getMessage}")
          case Right(_) => succeed
        }
      }
    }

    "create table" ignore {
      createTable()
    }

    "destroy table" ignore {
      destroyTable()
    }
  }

  /** NB: Only use these for local testing use the provided CloudFormation
    * template to create table in AWS environments.
    *
    * If you update this then be sure to also update the CloudFormation
    * template's definition.
    */
  private[aws] def createTable()(implicit
      dynamoDB: DynamoDbClient
  ): CreateTableResponse = {
    dynamoDB.createTable(
      CreateTableRequest
        .builder()
        .tableName(PasskeyChallengeDB.tableName)
        .keySchema(
          KeySchemaElement
            .builder()
            .attributeName("username")
            .keyType(HASH)
            .build()
        )
        .attributeDefinitions(
          AttributeDefinition
            .builder()
            .attributeName("username")
            .attributeType(S)
            .build()
        )
        .provisionedThroughput(
          ProvisionedThroughput
            .builder()
            .readCapacityUnits(15L)
            .writeCapacityUnits(15L)
            .build()
        )
        .build()
    )
  }

  private[aws] def destroyTable()(implicit
      dynamoDb: DynamoDbClient
  ): DeleteTableResponse =
    dynamoDb.deleteTable(
      DeleteTableRequest
        .builder()
        .tableName(PasskeyChallengeDB.tableName)
        .build()
    )
}
