package aws

import com.gu.janus.model.{ACL, AuditLog, AwsAccount}
import logic.{AccountUsage, AuditTrail}
import models.{AccountUsageReport, DeveloperPolicy}
import play.api.Logging
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator._
import software.amazon.awssdk.services.dynamodb.model._

import java.time.ZoneOffset.UTC
import java.time.{Instant, ZonedDateTime}
import scala.jdk.CollectionConverters._
import scala.util.Try

object AuditTrailDB extends Logging {
  import AuditTrail._

  def insert(auditLog: AuditLog)(using dynamoDB: DynamoDbClient): Unit = {
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
  )(using dynamoDB: DynamoDbClient): Seq[Either[String, AuditLog]] =
    queryResult(dynamoDB, accountLogsRequest(account, startDate, endDate))

  /** Reads every access request for an account over the given range.
    *
    * Ranges long enough to be useful for usage reporting exceed the 1MB limit
    * on a single query response, so this pages through the results.
    */
  def getAllAccountLogs(
      account: String,
      startDate: Instant,
      endDate: Instant
  )(using dynamoDB: DynamoDbClient): Seq[Either[String, AuditLog]] =
    dynamoDB
      .queryPaginator(accountLogsRequest(account, startDate, endDate))
      .items()
      .asScala
      .map(attrs => auditLogFromAttrs(attrs.asScala.toMap))
      .map(logDbResultErrs)
      .map(errorStrings)
      .toSeq

  /** How far back the access and usage report looks. */
  private val usagePeriodMonths = 3

  /** Builds the access and usage report for an account, or explains why it
    * could not be built so callers can fall back to listing users.
    */
  def accountUsageReport(
      account: AwsAccount,
      acl: ACL,
      accountDeveloperPolicies: Set[DeveloperPolicy],
      policyCacheError: Option[String]
  )(using dynamoDB: DynamoDbClient): Either[String, AccountUsageReport] = {
    val to = Instant.now()
    val from =
      ZonedDateTime.ofInstant(to, UTC).minusMonths(usagePeriodMonths).toInstant
    logger.info(
      s"Getting access and usage for ${account.authConfigKey} from $from to $to"
    )
    Try(getAllAccountLogs(account.authConfigKey, from, to)).fold(
      { error =>
        logger.error(
          s"Failed to read audit logs for ${account.authConfigKey}",
          error
        )
        Left("Could not read the audit trail for this account.")
      },
      auditLogs =>
        Right(
          AccountUsage.report(
            account = account,
            acl = acl,
            accountDeveloperPolicies = accountDeveloperPolicies,
            auditLogs = auditLogs,
            policyCacheError = policyCacheError,
            from = from,
            to = to
          )
        )
    )
  }

  private def accountLogsRequest(
      account: String,
      startDate: Instant,
      endDate: Instant
  ): QueryRequest =
    QueryRequest
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

  def getUserLogs(
      username: String,
      startDate: Instant,
      endDate: Instant
  )(using dynamoDB: DynamoDbClient): Seq[Either[String, AuditLog]] = {
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
