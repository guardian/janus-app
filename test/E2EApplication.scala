import com.google.auth.oauth2.ServiceAccountCredentials
import com.gu.googleauth.GoogleGroupChecker
import com.gu.janus.model.JanusData
import com.gu.play.secretrotation.{DualSecretTransition, SnapshotProvider}
import com.typesafe.config.ConfigFactory
import play.api.*
import play.api.inject.DefaultApplicationLifecycle
import play.core.server.{PekkoHttpServer, ServerConfig}

import java.io.{File, InputStream}
import java.net.{BindException, InetAddress, ServerSocket}
import java.security.KeyPairGenerator
import java.util.{Properties, UUID}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*
import scala.util.{Try, Using}
import scala.util.control.NonFatal

/** Runs one Janus application on its own local port with its own JanusData.
  *
  * This is a way of running isolated E2E tests using different JanusData
  * without having to set up a new server for each test. Tests can use different
  * fixtures and run at the same time.
  *
  * See [[E2ETestServer]].
  */
final class E2EApplication private (
    val baseURL: String,
    server: PekkoHttpServer
) {
  private lazy val stopped: Unit = server.stop()

  def stop(): Unit = stopped
}

object E2EApplication {
  def start(data: JanusData): E2EApplication =
    start(data, attempts = 3)

  private def start(data: JanusData, attempts: Int): E2EApplication = {
    val port = Using.resource(
      new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    )(_.getLocalPort)
    try startOnPort(data, port)
    catch {
      case NonFatal(error) if attempts > 1 && isBindFailure(error) =>
        start(data, attempts - 1)
    }
  }

  private def isBindFailure(error: Throwable): Boolean =
    error.isInstanceOf[BindException] ||
      Option(error.getCause).exists(isBindFailure)

  private def startOnPort(data: JanusData, port: Int): E2EApplication = {
    val baseURL = s"http://127.0.0.1:$port"
    val lifecycle = new DefaultApplicationLifecycle()
    val configuration = Configuration("host" -> baseURL)
      .withFallback(Configuration(ConfigFactory.load("e2e")))
    try {
      // Play's server shutdown also shuts down logging for its classloader.
      // Logging belongs to the warm JVM, not to any individual application.
      val classLoader =
        new ClassLoader(E2EApplication.getClass.getClassLoader) {
          override def getResourceAsStream(name: String): InputStream =
            if (name == "logger-configurator.properties")
              InputStream.nullInputStream()
            else super.getResourceAsStream(name)
        }
      val context = ApplicationLoader.Context(
        Environment(new File("."), classLoader, Mode.Test),
        configuration,
        lifecycle,
        None
      )
      val components = new AppComponents(context) {
        override lazy val janusData: JanusData = data

        override protected def createSecretStateSupplier(): SnapshotProvider =
          DualSecretTransition.InitialSecret(UUID.randomUUID().toString)

        lifecycle.addStopHook(() => {
          dynamodDB.close()
          Future.successful(())
        })

        override lazy val googleGroupChecker: GoogleGroupChecker = {
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
      val server = PekkoHttpServer.fromApplication(
        components.application,
        new ServerConfig(
          rootDir = new File("."),
          port = Some(port),
          sslPort = None,
          address = "127.0.0.1",
          mode = Mode.Test,
          properties = new Properties(),
          configuration = Configuration(
            "play.server.dir" -> new File(".").getAbsolutePath
          ).withFallback(configuration)
        )
      )
      new E2EApplication(baseURL, server)
    } catch {
      case NonFatal(error) =>
        Try(Await.result(lifecycle.stop(), 30.seconds)).failed
          .foreach(error.addSuppressed)
        throw error
    }
  }
}
