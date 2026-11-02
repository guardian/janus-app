import com.google.auth.oauth2.ServiceAccountCredentials
import com.gu.googleauth.GoogleGroupChecker
import com.gu.play.secretrotation.{DualSecretTransition, SnapshotProvider}
import com.typesafe.config.ConfigFactory
import play.api.inject.DefaultApplicationLifecycle
import play.api.*
import play.core.server.{PekkoHttpServer, ServerConfig}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import java.io.File
import java.security.KeyPairGenerator
import java.util.UUID
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

/** Test-classpath-only server for E2E test. */
object E2ETestServer {
  def main(args: Array[String]): Unit = {
    val app = application()
    try {
      val server = PekkoHttpServer.fromApplication(
        app,
//      ServerConfig(port = Some(9102), address = "127.0.0.1", mode = Mode.Test)
        ServerConfig(port = Some(9102), mode = Mode.Test)
      )
      val _ = sys.addShutdownHook(server.stop())
    } catch {
      case NonFatal(error) =>
        Await.result(app.stop(), 10.seconds)
        throw error
    }
  }

  private def application(): Application = {
    val environment =
      Environment(new File("."), getClass.getClassLoader, Mode.Test)
    val config = Configuration(ConfigFactory.load("e2e"))
    val lifecycle = new DefaultApplicationLifecycle()
    val database =
      E2EAuditFixtures.createClient(config.get[String]("e2e.dynamodb.endpoint"))
    lifecycle.addStopHook(() => Future.successful(database.close()))
    val context = ApplicationLoader.Context(
      environment,
      config,
      lifecycle,
      None
    )
    try {
      E2EAuditFixtures.seed()(using database)
      val components = new AppComponents(context) {
        override protected def createDynamoDbClient(): DynamoDbClient = database

        override protected def createSecretStateSupplier(): SnapshotProvider =
          DualSecretTransition.InitialSecret(UUID.randomUUID().toString)

        override lazy val googleGroupChecker: GoogleGroupChecker = {
          // The bypass never calls Google; no real credentials are loaded.
          val credentials = ServiceAccountCredentials
            .newBuilder()
            .setClientId("browser-test")
            .setClientEmail("browser-test@example.invalid")
            .setPrivateKey(
              KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate
            )
            .setPrivateKeyId("browser-test")
            .build()
          new GoogleGroupChecker("browser-test@example.invalid", credentials)
        }
      }
      components.application
    } catch {
      case NonFatal(error) =>
        Await.result(lifecycle.stop(), 10.seconds)
        throw error
    }
  }
}
