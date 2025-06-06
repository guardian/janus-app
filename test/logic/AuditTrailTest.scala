package logic

import com.gu.janus.model.{AuditLog, JConsole, JCredentials}
import com.gu.janus.testutils.{HaveMatchers, RightValues}
import logic.AuditTrail._
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import java.time.ZoneOffset.UTC
import java.time.{Duration, ZoneOffset, ZonedDateTime}
import scala.language.implicitConversions

class AuditTrailTest
    extends AnyFreeSpec
    with Matchers
    with RightValues
    with OptionValues
    with HaveMatchers {

  "auditLogAttrs" - {
    val al = AuditLog(
      "account",
      "username",
      ZonedDateTime.of(2015, 11, 4, 15, 22, 0, 0, UTC).toInstant,
      Duration.ofHours(1),
      "accessLevel",
      JCredentials,
      external = true
    )

    "sets up the hash key" in {
      val attrs = AuditLogDbEntryAttrs.fromAuditLog(al)
      val (_, value) = attrs.partitionKey
      value.s() shouldEqual "account"
    }

    "sets up the (date) range key as milliseconds" in {
      val attrs = AuditLogDbEntryAttrs.fromAuditLog(al)
      val (_, value) = attrs.sortKey
      value.n().toLong shouldEqual 1446650520000L
    }

    "sets up the (date) range key correctly even when BST is in effect" in {
      val al2 = al.copy(
        instant = ZonedDateTime
          .of(2015, 11, 4, 16, 22, 0, 0, ZoneOffset.ofHours(1))
          .toInstant
      )
      val attrs = AuditLogDbEntryAttrs.fromAuditLog(al2)
      val (_, value) = attrs.sortKey
      value.n().toLong shouldEqual 1446650520000L
    }

    "converts duration type to seconds" in {
      val attrs = AuditLogDbEntryAttrs.fromAuditLog(al)
      val (_, value) = attrs.sessionDuration
      value.n().toInt shouldEqual 3600
    }

    "sets up other attributes with db fieldnames" in {
      val attrs = AuditLogDbEntryAttrs.fromAuditLog(al)
      attrs.toMap shouldEqual Map(
        accountPartitionKeyName -> AttributeValue.fromS("account"),
        timestampSortKeyName -> AttributeValue.fromN(1446650520000L.toString),
        userNameAttrName -> AttributeValue.fromS("username"),
        durationAttrName -> AttributeValue.fromN(3600.toString),
        accessLevelAttrName -> AttributeValue.fromS("accessLevel"),
        accessTypeAttrName -> AttributeValue.fromS("credentials"),
        isExternalAttrName -> AttributeValue.fromN(1.toString)
      )
    }

    "sets up console type correctly" in {
      val attrs =
        AuditLogDbEntryAttrs.fromAuditLog(al.copy(accessType = JConsole))
      val (_, value) = attrs.accessType
      value.s() shouldEqual "console"
    }
  }

  "auditLogFromAttrs" - {
    "given valid attributes" - {
      val attrs = Map(
        accountPartitionKeyName -> AttributeValue.fromS("account"),
        userNameAttrName -> AttributeValue.fromS("username"),
        timestampSortKeyName -> AttributeValue.fromN("1446650520000"),
        durationAttrName -> AttributeValue.fromN("3600"),
        accessLevelAttrName -> AttributeValue.fromS("dev"),
        accessTypeAttrName -> AttributeValue.fromS("console"),
        isExternalAttrName -> AttributeValue.fromN("1")
      )

      "extracts an audit log from valid attributes" in {
        AuditTrail.auditLogFromAttrs(attrs).value should have(
          "account" as "account",
          "username" as "username",
          "instant" as ZonedDateTime
            .of(2015, 11, 4, 15, 22, 0, 0, UTC)
            .toInstant,
          "duration" as Duration.ofHours(1),
          "accessLevel" as "dev",
          "accessType" as JConsole,
          "external" as true
        )
      }

      "extracts a correct (ms) duration from the DB\'s seconds field" in {
        AuditTrail
          .auditLogFromAttrs(attrs)
          .value
          .duration shouldEqual Duration.ofHours(1)
      }
    }

    "when missing a required field" - {
      val attrs = Map(
        // missing account
        userNameAttrName -> AttributeValue.fromS("username"),
        timestampSortKeyName -> AttributeValue.fromN("1446650520000"),
        durationAttrName -> AttributeValue.fromN("3600"),
        accessLevelAttrName -> AttributeValue.fromS("dev"),
        accessTypeAttrName -> AttributeValue.fromS("console"),
        isExternalAttrName -> AttributeValue.fromN("1")
      )

      "fails to extract an AccessLog record" in {
        AuditTrail.auditLogFromAttrs(attrs).isLeft shouldEqual true
      }

      "returns a useful error message when it fails" in {
        val (message, _) =
          AuditTrail.auditLogFromAttrs(attrs).left.getOrElse(("nope", "nope"))

        message should include("account")
      }
    }
  }
}
