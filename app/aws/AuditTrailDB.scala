package aws

import com.gu.janus.model.AuditLog
import logic.AuditTrail
import org.joda.time.DateTime
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator.{
  BETWEEN,
  EQ
}
import software.amazon.awssdk.services.dynamodb.model.KeyType.{HASH, RANGE}
import software.amazon.awssdk.services.dynamodb.model._

import scala.jdk.CollectionConverters._

object AuditTrailDB {
  import AuditTrail._

  val tableName = "AuditTrail"
  private val secondaryIndexName = "AuditTrailByUser"

  def getTable()(implicit dynamoDB: DynamoDbClient): TableDescription = {
    val request = DescribeTableRequest.builder().tableName(tableName).build()
    dynamoDB.describeTable(request).table()
  }

  def insert(table: TableDescription, auditLog: AuditLog)(implicit
      dynamoDB: DynamoDbClient
  ): Unit = {
    val keySchema = table.keySchema().asScala
    val partitionKeyName =
      keySchema.find(_.keyType() == HASH).get.attributeName()
    val sortKeyName = keySchema.find(_.keyType() == RANGE).get.attributeName()
    val (hash_project, range_date, attrs) = auditLogAttrs(auditLog)
    val partitionKey = partitionKeyName -> AttributeValue.fromS(hash_project)
    val sortKey = sortKeyName -> AttributeValue.fromN(range_date.toString)
    val item = (attrs.toMap.view
      .mapValues(toAttribValue)
      .toMap + partitionKey + sortKey).asJava
    val request = PutItemRequest
      .builder()
      .tableName(tableName)
      .item(item)
      .build()
    dynamoDB.putItem(request)
  }

  private def toAttribValue(value: Any): AttributeValue = {
    value match {
      case s: String  => AttributeValue.fromS(s)
      case i: Int     => AttributeValue.fromN(i.toString)
      case l: Long    => AttributeValue.fromN(l.toString)
      case d: Double  => AttributeValue.fromN(d.toString)
      case b: Boolean => AttributeValue.fromBool(b)
      case _ =>
        throw new IllegalArgumentException(
          s"Unsupported type: ${value.getClass}"
        )
    }
  }

  def getAccountLogs(
      table: TableDescription,
      account: String,
      startDate: DateTime,
      endDate: DateTime
  )(implicit dynamoDB: DynamoDbClient): Seq[Either[String, AuditLog]] = {
    val request = QueryRequest
      .builder()
      .tableName(table.tableName())
      .keyConditions(
        Map(
          "j_account" -> Condition
            .builder()
            .comparisonOperator(EQ)
            .attributeValueList(AttributeValue.fromS(account))
            .build(),
          dateRangeCondition(startDate, endDate)
        ).asJava
      )
      .scanIndexForward(false)
      .build()
    queryResult(dynamoDB, request)
  }

  def getUserLogs(
      table: TableDescription,
      username: String,
      startDate: DateTime,
      endDate: DateTime
  )(implicit dynamoDB: DynamoDbClient): Seq[Either[String, AuditLog]] = {
    val request = QueryRequest
      .builder()
      .tableName(table.tableName())
      .indexName(secondaryIndexName)
      .keyConditions(
        Map(
          "j_username" -> Condition
            .builder()
            .comparisonOperator(EQ)
            .attributeValueList(AttributeValue.fromS(username))
            .build(),
          dateRangeCondition(startDate, endDate)
        ).asJava
      )
      .scanIndexForward(false)
      .build()
    queryResult(dynamoDB, request)
  }

  private def dateRangeCondition(
      startDate: DateTime,
      endDate: DateTime
  ): (String, Condition) = {
    "j_timestamp" -> Condition
      .builder()
      .comparisonOperator(BETWEEN)
      .attributeValueList(
        AttributeValue.fromN(startDate.getMillis.toString),
        AttributeValue.fromN(endDate.getMillis.toString)
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
