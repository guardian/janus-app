package aws

import com.gu.janus.model.{AuditLog, JConsole}
import org.joda.time.{DateTime, DateTimeZone, Duration}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.KeyType.{HASH, RANGE}
import software.amazon.awssdk.services.dynamodb.model._

class AuditTrailDBTest extends AnyFreeSpec with Matchers {

  "test db stuff - use this to test DynamoDB stuff locally during development" - {
    implicit val dynamoDB: DynamoDbClient = Clients.localDb

    "insertion and querying" ignore {
      val table = AuditTrailDB.getTable()
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
      AuditTrailDB.insert(table, al)

      val accountResults = AuditTrailDB.getAccountLogs(
        table,
        "account",
        dateTime.minusDays(1),
        dateTime.plusDays(1)
      )
      println(accountResults.toList)

      val userResults = AuditTrailDB.getUserLogs(
        table,
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
      .tableName(AuditTrailDB.tableName)
      .keySchema(
        KeySchemaElement
          .builder()
          .attributeName("j_account")
          .keyType(HASH)
          .build(),
        KeySchemaElement
          .builder()
          .attributeName("j_timestamp")
          .keyType(RANGE)
          .build()
      )
      .attributeDefinitions(
        AttributeDefinition
          .builder()
          .attributeName("j_account")
          .attributeType("S")
          .build(),
        AttributeDefinition
          .builder()
          .attributeName("j_timestamp")
          .attributeType("N")
          .build(),
        AttributeDefinition
          .builder()
          .attributeName("j_username")
          .attributeType("S")
          .build()
      )
      .globalSecondaryIndexes(
        GlobalSecondaryIndex
          .builder()
          .indexName("AuditTrailByUser")
          .keySchema(
            KeySchemaElement
              .builder()
              .attributeName("j_username")
              .keyType(HASH)
              .build(),
            KeySchemaElement
              .builder()
              .attributeName("j_timestamp")
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
      DeleteTableRequest.builder().tableName(AuditTrailDB.tableName).build()
    dynamoDB.deleteTable(request)
  }
}
