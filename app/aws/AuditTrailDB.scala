package aws

import awscala.dynamodbv2.{DynamoDBCondition, _}
import com.gu.janus.model.AuditLog
import logic.AuditTrail
import org.joda.time.DateTime

object AuditTrailDB {
  import AuditTrail._

  val tableName = "AuditTrail"
  val secondaryIndexName = "AuditTrailByUser"

  def getTable()(implicit dynamoDB: DynamoDB): Table =
    dynamoDB.table(tableName).get

  def insert(table: Table, auditLog: AuditLog)(implicit dynamoDB: DynamoDB) = {
    val (hash_project, range_date, attrs) = auditLogAttrs(auditLog)
    table.put(hash_project, range_date, attrs: _*)
  }

  def getAccountLogs(
      table: Table,
      account: String,
      startDate: DateTime,
      endDate: DateTime
  )(implicit dynamoDB: DynamoDB): Seq[Either[String, AuditLog]] = {
    dynamoDB
      .query(
        table,
        keyConditions = Seq(
          "j_account" -> DynamoDBCondition.eq(account),
          "j_timestamp" -> DynamoDBCondition
            .between(startDate.getMillis, endDate.getMillis)
        ),
        scanIndexForward = false
      )
      .map(
        _.attributes
      ) map auditLogFromAttrs map logDbResultErrs map errorStrings
  }

  def getUserLogs(
      table: Table,
      username: String,
      startDate: DateTime,
      endDate: DateTime
  )(implicit dynamoDB: DynamoDB): Seq[Either[String, AuditLog]] = {
    dynamoDB
      .queryWithIndex(
        table,
        index = IndexName(secondaryIndexName),
        keyConditions = Seq(
          "j_username" -> DynamoDBCondition.eq(username),
          "j_timestamp" -> DynamoDBCondition
            .between(startDate.getMillis, endDate.getMillis)
        ),
        scanIndexForward = false
      )
      .map(
        _.attributes
      ) map auditLogFromAttrs map logDbResultErrs map errorStrings
  }
}
