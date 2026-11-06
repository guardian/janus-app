package aws

import com.gu.janus.model.{AuditLog, JConsole}
import logic.AuditTrail
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.KeyType.{HASH, RANGE}
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.{N, S}
import software.amazon.awssdk.services.dynamodb.model._

import java.time.ZoneOffset.UTC
import java.time.{Duration, ZonedDateTime}

class AuditTrailDBTest extends AnyFreeSpec with Matchers {

  "test db stuff - use this to test DynamoDB stuff locally during development" - {
    given dynamoDB: DynamoDbClient = Clients.localDb

    "insertion and querying" ignore {
      val dateTime = ZonedDateTime.of(2015, 11, 5, 17, 35, 0, 0, UTC).toInstant
      val al = AuditLog(
        "account",
        "username",
        dateTime,
        Duration.ofHours(1),
        "accessLevel",
        JConsole,
        external = true
      )
      AuditTrailDB.insert(al)

      val accountResults = AuditTrailDB.getAccountLogs(
        "account",
        dateTime.minus(Duration.ofDays(1)),
        dateTime.plus(Duration.ofDays(1))
      )
      println(accountResults.toList)

      val userResults = AuditTrailDB.getUserLogs(
        "username",
        dateTime.minus(Duration.ofDays(1)),
        dateTime.plus(Duration.ofDays(1))
      )
      println(userResults.toList)
    }

  }

}
