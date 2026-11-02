import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import scala.concurrent.{blocking, Await, Future}
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

class E2EApplicationManagerTest extends AnyFreeSpec with Matchers {
  "applications have independent lifecycles and are closed on manager shutdown" in {
    val manager = new E2EApplicationManager()
    val client = HttpClient
      .newBuilder()
      .connectTimeout(Duration.ofSeconds(1))
      .build()
    def healthcheck(baseURL: String): Try[Int] = Try {
      client
        .send(
          HttpRequest
            .newBuilder(URI.create(s"$baseURL/healthcheck"))
            .timeout(Duration.ofSeconds(2))
            .build(),
          HttpResponse.BodyHandlers.discarding()
        )
        .statusCode()
    }

    try {
      val instances = Await.result(
        Future.traverse(
          List("index-permission", "accounts-list")
        )(fixture =>
          Future(blocking(manager.create(E2EJanusData.fixtures(fixture))))
        ),
        30.seconds
      )
      val firstURL = instances.head._2
      val secondURL = instances(1)._2
      healthcheck(firstURL).get shouldEqual 200
      healthcheck(secondURL).get shouldEqual 200

      manager.delete(instances.head._1)
      healthcheck(firstURL).isFailure shouldBe true
      healthcheck(secondURL).get shouldEqual 200

      manager.close()

      healthcheck(firstURL).isFailure shouldBe true
      healthcheck(secondURL).isFailure shouldBe true
      an[IllegalArgumentException] should be thrownBy
        manager.create(E2EJanusData.fixtures("index-permission"))
    } finally manager.close()
  }
}
