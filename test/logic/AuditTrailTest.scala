package logic


import awscala.dynamodbv2.{Attribute, AttributeValue}
import com.gu.janus.model.{AuditLog, JConsole, JCredentials}
import com.gu.janus.testutils.{HaveMatchers, RightValues}
import org.joda.time.{DateTime, DateTimeZone, Duration}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{OptionValues}

import scala.language.implicitConversions

class AuditTrailTest extends AnyFreeSpec with Matchers with RightValues with OptionValues with HaveMatchers {
  "auditLogAttrs" - {
    val al = AuditLog("account", "username", new DateTime(2015, 11, 4, 15, 22), new Duration(3600 * 1000), "accessLevel", JCredentials, external = true)

    "sets up the hash key" in {
      val (hashKey, _, _) = AuditTrail.auditLogAttrs(al)
      hashKey shouldEqual "account"
    }

    "sets up the (date) range key as milliseconds" in {
      val (_, rangeKey, _) = AuditTrail.auditLogAttrs(al)
      rangeKey shouldEqual 1446650520000L
    }

    "sets up the (date) range key correctly even when BST is in effect" in {
      val al2 = al.copy(dateTime = new DateTime(2015, 11, 4, 16, 22, DateTimeZone.forOffsetHours(1)))
      //                        hour and timezone changed ---^--------------------^
      val (_, rangeKey, _) = AuditTrail.auditLogAttrs(al2)
      rangeKey shouldEqual 1446650520000L
    }

    "converts duration type to seconds" in {
      val (_, _, attrs) = AuditTrail.auditLogAttrs(al)
      attrs.find(_._1 == "j_duration").map(_._2).value shouldEqual 3600
    }

    "sets up other attributes with db fieldnames" in {
      val (_, _, attrs) = AuditTrail.auditLogAttrs(al)
      attrs shouldEqual List(
        "j_username" -> "username",
        "j_duration" -> 3600,
        "j_accessLevel" -> "accessLevel",
        "j_accessType" -> "credentials",
        "j_external" -> 1
      )
    }

    "sets up console type correctly" in {
      val (_, _, attrs) = AuditTrail.auditLogAttrs(al.copy(accessType = JConsole))
      attrs should contain("j_accessType" -> "console")
    }
  }

  "auditLogFromAttrs" - {
    "given valid attributes" - {
      val attrs = Seq(
        Attribute("j_account", AttributeValue(s = Some("account"), l = Nil)),
        Attribute("j_username", AttributeValue(s = Some("username"), l = Nil)),
        Attribute("j_timestamp", AttributeValue(n = Some("1446650520000"), l = Nil)),
        Attribute("j_duration", AttributeValue(n = Some("3600"), l = Nil)),
        Attribute("j_accessLevel", AttributeValue(s = Some("dev"), l = Nil)),
        Attribute("j_accessType", AttributeValue(s = Some("console"), l = Nil)),
        Attribute("j_external", AttributeValue(n = Some("1"), l = Nil))
      )

      "extracts an audit log from valid attributes" in {
        AuditTrail.auditLogFromAttrs(attrs).value should have(
          "account" as "account",
          "username" as "username",
          "dateTime" as new DateTime(2015, 11, 4, 15, 22, DateTimeZone.UTC),
          "duration" as new Duration(3600000),
          "accessLevel" as "dev",
          "accessType" as JConsole,
          "external" as true
        )
      }

      "extracts a correct (ms) duration from the DB's seconds field" in {
        AuditTrail.auditLogFromAttrs(attrs).value.duration shouldEqual new Duration(3600 * 1000)
      }
    }

    "when missing a required field" - {
      val attrs = Seq(
        // missing account
        Attribute("j_username", AttributeValue(s = Some("username"), l = Nil)),
        Attribute("j_timestamp", AttributeValue(n = Some("1446650520000"), l = Nil)),
        Attribute("j_duration", AttributeValue(n = Some("3600"), l = Nil)),
        Attribute("j_accessLevel", AttributeValue(s = Some("dev"), l = Nil)),
        Attribute("j_accessType", AttributeValue(s = Some("console"), l = Nil)),
        Attribute("j_external", AttributeValue(n = Some("1"), l = Nil))
      )

      "fails to extract an AccessLog record" in {
        AuditTrail.auditLogFromAttrs(attrs).isLeft shouldEqual true
      }

      "returns a useful error message when it fails" in {
        val (message, _) = AuditTrail.auditLogFromAttrs(attrs)
          .left.getOrElse(("nope", "nope"))
        
        message should include("account")
      }
    }
  }
}
