package filters

import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.janus.model.{ACL, JanusData, SupportACL}
import controllers.AssetsFinder
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.Mode
import play.api.mvc.{AnyContentAsFormUrlEncoded, Cookie, Results}
import play.api.test.{FakeHeaders, FakeRequest}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

import java.time.Duration
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
  private val testCookieName = "test-cookie"
  private val testUser = UserIdentity(
    sub = "test-sub-id",
    email = "test@example.com",
    firstName = "Test",
    lastName = "User",
    exp = Long.MaxValue,
    avatarUrl = None
  )

  private def createRequestWithCookie[A](body: A) = {
    val cookie = Cookie(testCookieName, "present")
    new AuthAction.UserIdentityRequest(
      testUser,
      FakeRequest("POST", "/test")
        .withHeaders(FakeHeaders())
        .withBody(body)
        .withCookies(cookie)
    )
  }

  private def createRequestWithoutCookie[A](body: A) = {
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
    given mode: Mode = Mode.Test

    given assetsFinder: AssetsFinder = new AssetsFinder {
      override def assetsBasePath: String = "assetsBasePath"
      override def assetsUrlPrefix: String = "assetsUrlPrefix"
      override def findAssetPath(basePath: String, rawPath: String): String =
        "assetPath"
    }

    val janusData = JanusData(
      accounts = Set.empty,
      ACL(Map.empty),
      ACL(Map.empty),
      SupportACL.create(Map.empty, Set.empty, Duration.ofDays(7)),
      Some("https://example.com/")
    )

    "bypass authentication when disabled" in {
      val filter = new PasskeyAuthFilter(
        host = testHost,
        janusData = janusData,
        passkeysEnabled = false,
        enablingCookieName = testCookieName
      )

      val request = createRequestWithCookie(validFormBody)
      val result = filter.filter(request).futureValue

      result shouldBe None
    }

    "bypass authentication when enabling cookie is not present" in {
      val filter = new PasskeyAuthFilter(
        host = testHost,
        janusData = janusData,
        passkeysEnabled = true,
        enablingCookieName = testCookieName
      )

      val request = createRequestWithoutCookie(validFormBody)
      val result = filter.filter(request).futureValue

      result shouldBe None
    }
  }
}
