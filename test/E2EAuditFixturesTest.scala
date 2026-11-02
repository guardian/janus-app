import logic.AuditTrail
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class E2EAuditFixturesTest extends AnyFreeSpec with Matchers {
  "audit fixtures round-trip through the production serializer" in {
    E2EAuditFixtures.records.foreach { record =>
      val attributes =
        AuditTrail.AuditLogDbEntryAttrs.fromAuditLog(record).toMap
      AuditTrail.auditLogFromAttrs(attributes) shouldEqual Right(record)
    }
  }

  "audit fixtures have distinct database keys" in {
    val keys = E2EAuditFixtures.records.map(record =>
      (record.account, record.instant.toEpochMilli)
    )
    keys.distinct shouldEqual keys
  }

  "the E2E client refuses development and AWS endpoints" in {
    List(
      "http://localhost:8000",
      "http://127.0.0.1:8000",
      "https://dynamodb.eu-west-1.amazonaws.com"
    ).foreach { endpoint =>
      an[IllegalArgumentException] should be thrownBy {
        E2EAuditFixtures.createClient(endpoint)
      }
    }
  }
}
