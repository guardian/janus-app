package filters

import com.gu.googleauth.{AuthAction, UserIdentity}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc.{AnyContentAsFormUrlEncoded, Results}
import play.api.test.{FakeHeaders, FakeRequest}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import scala.concurrent.ExecutionContext.Implicits.global

class PasskeyAuthFilterTest
    extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with Results {

  given DynamoDbClient = {
    // Stub implementation for testing
    val mockClient = new DynamoDbClient {
      override def serviceName(): String = "dynamodb-mock"
      override def close(): Unit = {}
    }
    mockClient
  }

  private val testHost = "test.example.com"
  private val testUser = UserIdentity(
    sub = "test-sub-id",
    email = "test@example.com",
    firstName = "Test",
    lastName = "User",
    exp = Long.MaxValue,
    avatarUrl = None
  )

  private def createRequest[A](body: A) = {
    new AuthAction.UserIdentityRequest(
      testUser,
      FakeRequest("POST", "/test")
        .withHeaders(FakeHeaders())
        .withBody(body)
    )
  }

  private val validFormBody = AnyContentAsFormUrlEncoded(
    Map(
      "credentials" -> Seq(
        """{"id":"ABCDEFGHIJKLMNOPQRSTUVWXYZ","rawId":"ABCDEFGHIJKLMNOPQRSTUVWXYZ","response":{"authenticatorData":"data","clientDataJSON":"json","signature":"sig"}}"""
      )
    )
  )

  "PasskeyAuthFilter" - {

    "bypass authentication when disabled" in {
      val filter = new PasskeyAuthFilter(
        host = testHost,
        passkeysEnabled = false
      )

      val request = createRequest(validFormBody)
      val result = filter.filter(request).futureValue

      result shouldBe None
    }
  }
}
