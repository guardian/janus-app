package aws

import com.gu.janus.model.{AuditLog, JConsole}
import logic.AuditTrail
import org.joda.time.{DateTime, DateTimeZone, Duration}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.KeyType.{HASH, RANGE}
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.{N, S}
import software.amazon.awssdk.services.dynamodb.model._

class AuditTrailDBTest extends AnyFreeSpec with Matchers {

  "test db stuff - use this to test DynamoDB stuff locally during development" - {
    implicit val dynamoDB: DynamoDbClient = Clients.localDb

    "insertion and querying" ignore {
      val dateTime: DateTime =
        new DateTime(2015, 11, 5, 17, 35, DateTimeZone.UTC)
      val al = AuditLog(
        "account",
        "username",
        dateTime,
        new Duration(3600000),
        "accessLevel",
        JConsole,
        external = true
      )
      AuditTrailDB.insert(al)

      val accountResults = AuditTrailDB.getAccountLogs(
        "account",
        dateTime.minusDays(1),
        dateTime.plusDays(1)
      )
      println(accountResults.toList)

      val userResults = AuditTrailDB.getUserLogs(
        "username",
        dateTime.minusDays(1),
        dateTime.plusDays(1)
      )
      println(userResults.toList)
    }

    "create database table" ignore {
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
    val auditTableCreateRequest = CreateTableRequest
      .builder()
      .tableName(AuditTrail.tableName)
      .keySchema(
        KeySchemaElement
          .builder()
          .attributeName(AuditTrail.partitionKeyName)
          .keyType(HASH)
          .build(),
        KeySchemaElement
          .builder()
          .attributeName(AuditTrail.sortKeyName)
          .keyType(RANGE)
          .build()
      )
      .attributeDefinitions(
        AttributeDefinition
          .builder()
          .attributeName(AuditTrail.partitionKeyName)
          .attributeType(S)
          .build(),
        AttributeDefinition
          .builder()
          .attributeName(AuditTrail.sortKeyName)
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
              .attributeName(AuditTrail.sortKeyName)
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
