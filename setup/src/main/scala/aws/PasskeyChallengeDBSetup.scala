package aws

import com.gu.googleauth.UserIdentity
import com.webauthn4j.data.client.challenge.DefaultChallenge
import models.PasskeyFlow.{Authentication, Registration}
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
class PasskeyChallengeDBSetup {

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
            .build(),
          KeySchemaElement
            .builder()
            .attributeName("flow")
            .keyType(RANGE)
            .build()
        )
        .attributeDefinitions(
          AttributeDefinition
            .builder()
            .attributeName("username")
            .attributeType(S)
            .build(),
          AttributeDefinition
            .builder()
            .attributeName("flow")
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
