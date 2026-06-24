package aws;

import com.gu.janus.model.{AuditLog, JConsole}
import logic.AuditTrail
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.KeyType.{HASH, RANGE}
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType.{N, S}
import software.amazon.awssdk.services.dynamodb.model._

class DBSetupAppTest extends AnyFreeSpec with Matchers {

  "parseArgs" - {
    "parses 'create' only correctly" in {
      val (create, destroy) = DBSetupApp.parseArgs(List("create"))
      create shouldBe true
      destroy shouldBe false
    }

    "parses 'destroy' only correctly" in {
      val (create, destroy) = DBSetupApp.parseArgs(List("destroy"))
      create shouldBe false
      destroy shouldBe true
    }

    "parses 'recreate' only correctly" in {
      val (create, destroy) = DBSetupApp.parseArgs(List("recreate"))
      create shouldBe true
      destroy shouldBe true
    }

    "parses 'create' and 'destroy' correctly" in {
      val (create, destroy) = DBSetupApp.parseArgs(List("create", "destroy"))
      create shouldBe true
      destroy shouldBe true
    }

    "fails to parse 'banana' and thus refuses to continue" in {
      assertThrows[Exception] {
        DBSetupApp.parseArgs(List("banana"))
      }
    }
  }
}
