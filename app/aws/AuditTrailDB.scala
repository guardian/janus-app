package aws

import com.gu.janus.model.AuditLog
import logic.AuditTrail
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator._
import software.amazon.awssdk.services.dynamodb.model._

import java.time.Instant
import scala.jdk.CollectionConverters._

object AuditTrailDB {
  import AuditTrail._

  def insert(auditLog: AuditLog)(implicit dynamoDB: DynamoDbClient): Unit = {
    val auditLogDbAttrs = AuditLogDbEntryAttrs.fromAuditLog(auditLog)
    val item = auditLogDbAttrs.toMap.asJava
    val request =
      PutItemRequest.builder().tableName(tableName).item(item).build()
    dynamoDB.putItem(request)
  }

  def getAccountLogs(
      account: String,
      startDate: Instant,
      endDate: Instant
  )(implicit dynamoDB: DynamoDbClient): Seq[Either[String, AuditLog]] = {
    val request = QueryRequest
      .builder()
      .tableName(tableName)
      .keyConditions(
        Map(
          attrEqualCondition(
            accountPartitionKeyName,
            AttributeValue.fromS(account)
          ),
          dateRangeCondition(startDate, endDate)
        ).asJava
      )
      .scanIndexForward(false)
      .build()
    queryResult(dynamoDB, request)
  }

  def getUserLogs(
      username: String,
      startDate: Instant,
      endDate: Instant
  )(implicit dynamoDB: DynamoDbClient): Seq[Either[String, AuditLog]] = {
    val request = QueryRequest
      .builder()
      .tableName(tableName)
      .indexName(secondaryIndexName)
      .keyConditions(
        Map(
          attrEqualCondition(userNameAttrName, AttributeValue.fromS(username)),
          dateRangeCondition(startDate, endDate)
        ).asJava
      )
      .scanIndexForward(false)
      .build()
    queryResult(dynamoDB, request)
  }

  private def attrEqualCondition(
      attrName: String,
      attrValue: AttributeValue
  ) =
    attrName -> Condition
      .builder()
      .comparisonOperator(EQ)
      .attributeValueList(attrValue)
      .build()

  private def dateRangeCondition(
      startDate: Instant,
      endDate: Instant
  ): (String, Condition) = {
    timestampSortKeyName -> Condition
      .builder()
      .comparisonOperator(BETWEEN)
      .attributeValueList(
        AttributeValue.fromN(startDate.toEpochMilli.toString),
        AttributeValue.fromN(endDate.toEpochMilli.toString)
      )
      .build()
  }

  private def queryResult(
      dynamoDB: DynamoDbClient,
      request: QueryRequest
  ): Seq[Either[String, AuditLog]] = {
    val result = dynamoDB.query(request)
    result
      .items()
      .asScala
      .map(attrs => auditLogFromAttrs(attrs.asScala.toMap))
      .map(logDbResultErrs)
      .map(errorStrings)
      .toSeq
  }
}
