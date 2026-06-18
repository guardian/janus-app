package aws

import com.gu.janus.model.{AuditLog, JConsole}
import logic.AuditTrail
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.KeyType.{HASH, RANGE}
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.{N, S}
import software.amazon.awssdk.services.dynamodb.model._

import java.time.ZoneOffset.UTC
import java.time.{Duration, ZonedDateTime}

class AuditTrailDBSetup {

  /** NB: Only use these for local testing use the provided CloudFormation
    * template to create table in AWS environments.
    *
    * If you update this then be sure to also update the CloudFormation
    * template's definition.
    */
  private[aws] def createTable()(implicit
      dynamoDB: DynamoDbClient
  ): CreateTableResponse = {
    val auditTableCreateRequest = CreateTableRequest
      .builder()
      .tableName(AuditTrail.tableName)
      .keySchema(
        KeySchemaElement
          .builder()
          .attributeName(AuditTrail.accountPartitionKeyName)
          .keyType(HASH)
          .build(),
        KeySchemaElement
          .builder()
          .attributeName(AuditTrail.timestampSortKeyName)
          .keyType(RANGE)
          .build()
      )
      .attributeDefinitions(
        AttributeDefinition
          .builder()
          .attributeName(AuditTrail.accountPartitionKeyName)
          .attributeType(S)
          .build(),
        AttributeDefinition
          .builder()
          .attributeName(AuditTrail.timestampSortKeyName)
          .attributeType(N)
          .build(),
        AttributeDefinition
          .builder()
          .attributeName(AuditTrail.userNameAttrName)
          .attributeType(S)
          .build()
      )
      .globalSecondaryIndexes(
        GlobalSecondaryIndex
          .builder()
          .indexName(AuditTrail.secondaryIndexName)
          .keySchema(
            KeySchemaElement
              .builder()
              .attributeName(AuditTrail.userNameAttrName)
              .keyType(HASH)
              .build(),
            KeySchemaElement
              .builder()
              .attributeName(AuditTrail.timestampSortKeyName)
              .keyType(RANGE)
              .build()
          )
          .projection(Projection.builder().projectionType("ALL").build())
          .provisionedThroughput(
            ProvisionedThroughput
              .builder()
              .readCapacityUnits(15L)
              .writeCapacityUnits(15L)
              .build()
          )
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

    dynamoDB.createTable(auditTableCreateRequest)
  }

  private[aws] def destroyTable()(implicit
      dynamoDB: DynamoDbClient
  ): DeleteTableResponse = {
    val request =
      DeleteTableRequest.builder().tableName(AuditTrail.tableName).build()
    dynamoDB.deleteTable(request)
  }
}
