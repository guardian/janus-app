import com.sun.net.httpserver.{HttpExchange, HttpServer}
import com.typesafe.config.ConfigException
import e2e.E2EJanusData
import play.api.libs.json.{JsResultException, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.{blocking, ExecutionContext}
import scala.util.control.NonFatal

/** Server for per-test apps. This architecture gives us the ability to run
  * parallel Playwright tests with different JanusData config. Playwright makes
  * HTTP calls to start and stop servers.
  *
  *   1. Playwright starts E2ETestServer on port 9102.
  *   2. Before each test, [[fixtures.js]] sends POST /instances with a
  *      JanusData fixture name.
  *   3. [[E2EApplicationManager]] starts an [[E2EApplication]] on a separate
  *      local port. The server returns its ID and URL.
  *   4. The browser uses that URL to test Janus.
  *   5. After the test, [[fixtures.js]] sends DELETE /instances/{id} to stop
  *      the application.
  */
object E2ETestServer {
  def main(args: Array[String]): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 9102), 0)
    val manager = new E2EApplicationManager()
    server.setExecutor(ExecutionContext.global)
    server.createContext("/", exchange => blocking(handle(exchange, manager)))
    sys.addShutdownHook {
      try manager.close()
      finally server.stop(0)
    }
    server.start()
    println(
      "E2E application manager ready at http://127.0.0.1:9102/healthcheck"
    )
  }

  private def handle(
      exchange: HttpExchange,
      manager: E2EApplicationManager
  ): Unit = {
    try {
      (exchange.getRequestMethod, exchange.getRequestURI.getPath) match {
        case ("GET", "/healthcheck") =>
          respond(exchange, 200, """{"status":"ok"}""")
        case ("POST", "/instances") =>
          val bytes = exchange.getRequestBody.readNBytes(8193)
          val fixture = (Json.parse(bytes) \ "janusData").as[String]
          val data = E2EJanusData.fixtures.getOrElse(
            fixture,
            throw new IllegalArgumentException(
              s"Unknown JanusData fixture '$fixture'"
            )
          )
          val (id, baseURL) = manager.create(data)
          respond(
            exchange,
            201,
            Json.obj("id" -> id, "baseURL" -> baseURL).toString
          )
        case ("DELETE", path) if path.matches("/instances/[a-f0-9-]{36}") =>
          manager.delete(path.stripPrefix("/instances/"))
          exchange.sendResponseHeaders(204, -1)
        case _ =>
          respond(exchange, 404, """{"error":"Unknown control route"}""")
      }
    } catch {
      case error @ (_: IllegalArgumentException | _: JsResultException |
          _: ConfigException) =>
        respondError(exchange, 400, error)
      case NonFatal(error) =>
        error.printStackTrace()
        respondError(exchange, 500, error)
    } finally exchange.close()
  }

  private def respondError(
      exchange: HttpExchange,
      status: Int,
      error: Throwable
  ): Unit =
    respond(
      exchange,
      status,
      Json
        .obj("error" -> s"${error.getClass.getSimpleName}: ${error.getMessage}")
        .toString
    )

  private def respond(
      exchange: HttpExchange,
      status: Int,
      body: String
  ): Unit = {
    val bytes = body.getBytes(UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    exchange.getResponseBody.write(bytes)
  }
}
