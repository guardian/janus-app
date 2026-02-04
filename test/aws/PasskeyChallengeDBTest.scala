package aws

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.services.dynamodb.model.KeyType.{HASH, RANGE}
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.S

/** Integration tests of PasskeyChallengeDB.
  *
  * These tests require a local Dynamo DB service to be available. See
  * [[./local-dev/README.md#setting-up-passkeys-tables Setting up Passkeys tables]]
  */
class PasskeyChallengeDBTest extends AnyFreeSpec with Matchers {

  private val tableName = "PasskeyChallenges"

  "test db stuff - use this to test DynamoDB stuff locally during development" - {
    given dynamoDB: DynamoDbClient = Clients.localDb

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
        .tableName(tableName)
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
        .tableName(tableName)
        .build()
    )
}
