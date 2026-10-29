import com.google.auth.oauth2.ServiceAccountCredentials
import com.gu.googleauth.GoogleGroupChecker
import com.gu.play.secretrotation.{DualSecretTransition, SnapshotProvider}
import com.typesafe.config.ConfigFactory
import play.api.{
  Application,
  ApplicationLoader,
  Configuration,
  Environment,
  Mode
}
import play.api.inject.DefaultApplicationLifecycle
import play.core.server.{PekkoHttpServer, ServerConfig}

import java.io.File
import java.security.KeyPairGenerator
import java.util.UUID

/** Test-classpath-only server for the accounts browser smoke test. */
object BrowserTestServer {
  def main(args: Array[String]): Unit = {
    val server = PekkoHttpServer.fromApplication(
      application(),
      ServerConfig(port = Some(9100), address = "127.0.0.1", mode = Mode.Test)
    )
    val _ = sys.addShutdownHook(server.stop())
  }

  private def application(): Application = {
    val environment =
      Environment(new File("."), getClass.getClassLoader, Mode.Test)
    val context = ApplicationLoader.Context(
      environment,
      Configuration(ConfigFactory.load("browser")),
      new DefaultApplicationLifecycle(),
      None
    )
    val components = new AppComponents(context) {
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
  }
}
