import com.sun.net.httpserver.{HttpExchange, HttpServer}
import com.typesafe.config.ConfigException
import play.api.libs.json.{JsResultException, Json}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.{blocking, ExecutionContext}
import scala.util.control.NonFatal

/** Test-only control server on port 9102. Playwright sends `POST /instances`
  * with a fixture name; [[E2EApplicationManager]] starts an [[E2EApplication]]
  * and the server returns its ID and URL. Browser traffic goes directly to that
  * URL. Playwright sends `DELETE /instances/{id}` when the test ends.
  */
object E2ETestServer {
  def main(args: Array[String]): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 9102), 0)
    val manager = new E2EApplicationManager()
    server.setExecutor(ExecutionContext.global)
    val _ =
      server.createContext("/", exchange => blocking(handle(exchange, manager)))
    val _ = sys.addShutdownHook {
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
      require(
        !exchange.getRequestHeaders.containsKey("Origin"),
        "Browser access to the E2E control API is not allowed"
      )
      (exchange.getRequestMethod, exchange.getRequestURI.getPath) match {
        case ("GET", "/healthcheck") =>
          respond(exchange, 200, """{"status":"ok"}""")
        case ("POST", "/instances") =>
          require(
            Option(exchange.getRequestHeaders.getFirst("Content-Type"))
              .exists(_.split(";").head.trim == "application/json"),
            "Expected application/json"
          )
          val bytes = exchange.getRequestBody.readNBytes(8193)
          require(bytes.length <= 8192, "E2E control request is too large")
          val fixture = (Json.parse(bytes) \ "janusData").as[String]
          val data = E2EJanusData.fixtures
            .get(fixture)
            .getOrElse(
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
