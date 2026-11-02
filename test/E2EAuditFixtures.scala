import aws.AuditTrailDB
import com.gu.janus.model.{AuditLog, JConsole, JCredentials}
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.regions.Region.EU_WEST_1
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import java.net.URI
import java.time.{Duration, Instant}

object E2EAuditFixtures {
  val records: List[AuditLog] = List(
    AuditLog(
      "test-aws-account",
      "dev-user",
      Instant.parse("2020-05-12T10:00:00Z"),
      Duration.ofHours(1),
      "e2e-deny-all",
      JConsole,
      external = false
    ),
    AuditLog(
      "test-aws-account",
      "dev-user",
      Instant.parse("2020-05-12T11:00:00Z"),
      Duration.ofMinutes(30),
      "e2e-deny-all",
      JCredentials,
      external = true
    ),
    AuditLog(
      "test-aws-account",
      "e2e-other-user",
      Instant.parse("2020-05-12T12:00:00Z"),
      Duration.ofMinutes(15),
      "e2e-deny-all",
      JConsole,
      external = false
    )
  )

  def createClient(endpoint: String): DynamoDbClient = {
    require(
      endpoint == "http://127.0.0.1:18000",
      "E2E DynamoDB must use the isolated loopback endpoint on port 18000"
    )
    DynamoDbClient
      .builder()
      .endpointOverride(URI.create(endpoint))
      .region(EU_WEST_1)
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create("fakeMyKeyId", "fakeSecretAccessKey")
        )
      )
      .overrideConfiguration(
        ClientOverrideConfiguration
          .builder()
          .apiCallTimeout(Duration.ofSeconds(5))
          .apiCallAttemptTimeout(Duration.ofSeconds(2))
          .build()
      )
      .build()
  }

  def seed()(using DynamoDbClient): Unit = {
    records.foreach(AuditTrailDB.insert)
    val start = Instant.parse("2020-05-11T00:00:00Z")
    val end = Instant.parse("2020-05-18T00:00:00Z")
    val expectedAccount = records.reverse.map(Right(_))
    val expectedUser =
      records.reverse.filter(_.username == "dev-user").map(Right(_))

    def visible: Boolean =
      AuditTrailDB.getAccountLogs(
        "test-aws-account",
        start,
        end
      ) == expectedAccount &&
        AuditTrailDB.getUserLogs("dev-user", start, end) == expectedUser

    // The user index may become visible after the base-table writes.
    val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos
    var ready = visible
    while (!ready && System.nanoTime() < deadline) {
      Thread.sleep(100)
      ready = visible
    }
    require(
      ready,
      "E2E audit fixtures did not become visible in both query paths"
    )
  }
}
