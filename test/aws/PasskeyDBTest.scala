package aws

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.KeyType.{HASH, RANGE}
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.S
import software.amazon.awssdk.services.dynamodb.model._

class PasskeyDBTest extends AnyFreeSpec with Matchers {

  "test db stuff - use this to test DynamoDB stuff locally during development" - {
    implicit val dynamoDB: DynamoDbClient = Clients.localDb

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
  ): CreateTableResponse =
    dynamoDB.createTable(
      CreateTableRequest
        .builder()
        .tableName(PasskeyDB.tableName)
        .keySchema(
          KeySchemaElement
            .builder()
            .attributeName("userName")
            .keyType(HASH)
            .build(),
          KeySchemaElement
            .builder()
            .attributeName("credentialId")
            .keyType(RANGE)
            .build()
        )
        .attributeDefinitions(
          AttributeDefinition
            .builder()
            .attributeName("userName")
            .attributeType(S)
            .build(),
          AttributeDefinition
            .builder()
            .attributeName("credentialId")
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

  private[aws] def destroyTable()(implicit
      dynamoDb: DynamoDbClient
  ): DeleteTableResponse =
    dynamoDb.deleteTable(
      DeleteTableRequest.builder().tableName(PasskeyDB.tableName).build()
    )
}
