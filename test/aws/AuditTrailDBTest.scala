package aws

import awscala.dynamodbv2._
import com.gu.janus.model.{AuditLog, JConsole}
import org.joda.time.{DateTime, DateTimeZone, Duration}
import org.scalatest.{FreeSpec, Matchers}


class AuditTrailDBTest extends FreeSpec with Matchers {

  "test db stuff - use this to test DynamoDB stuff locally during development" - {
    implicit val dynamoDB = DynamoDB.local()

    "insertion and querying" ignore {
      val table = AuditTrailDB.getTable()
      val dateTime: DateTime = new DateTime(2015, 11, 5, 17, 35, DateTimeZone.UTC)
      val al = AuditLog("account", "username", dateTime, new Duration(3600000), "accessLevel", JConsole, external = true)
      AuditTrailDB.insert(table, al)

      val accountResults = AuditTrailDB.getAccountLogs(table, "account", dateTime.minusDays(1), dateTime.plusDays(1))
      println(accountResults.toList)

      val userResults = AuditTrailDB.getUserLogs(table, "username", dateTime.minusDays(1), dateTime.plusDays(1))
      println(userResults.toList)
    }

    "create database table" ignore {
      createTable()
    }

    "destroy table" ignore {
      destroyTable()
    }
  }


  /**
   * NB: Only use these for local testing
   * use the provided CloudFormation template to create table in AWS environments.
   *
   * If you update this then be sure to also update the cloudformation template's definition.
   */
  private[aws] def createTable()(implicit dynamoDB: DynamoDB) = {
    val auditTable = Table(
      name = AuditTrailDB.tableName,
      hashPK = "j_account",
      rangePK = Some("j_timestamp"),
      attributes = Seq(
        AttributeDefinition("j_account", AttributeType.String),
        AttributeDefinition("j_timestamp", AttributeType.Number),
        AttributeDefinition("j_username", AttributeType.String)
      ),
      globalSecondaryIndexes = Seq(
        GlobalSecondaryIndex(
          name = "AuditTrailByUser",
          keySchema = Seq(KeySchema("j_username", KeyType.Hash), KeySchema("j_timestamp", KeyType.Range)),
          projection = Projection(ProjectionType.All),
          provisionedThroughput = ProvisionedThroughput(15, 15)
        )
      ),
      provisionedThroughput = Some(ProvisionedThroughput(15, 15))
    )

    dynamoDB.createTable(auditTable)
  }
  private[aws] def destroyTable()(implicit dynamoDB: DynamoDB) = {
    AuditTrailDB.getTable().destroy()
  }
}
