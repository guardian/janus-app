package filters

import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.playpasskeyauth.models.{Passkey, PasskeyId, PasskeyName, UserId}
import com.gu.playpasskeyauth.services.PasskeyVerificationService
import com.webauthn4j.data.{
  AuthenticationData,
  PublicKeyCredentialCreationOptions,
  PublicKeyCredentialRequestOptions
}
import play.api.libs.json.JsValue
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc.{AnyContentAsFormUrlEncoded, Cookie, Results}
import play.api.test.{FakeHeaders, FakeRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PasskeyAuthFilterTest
    extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with Results {

  private val noOpVerificationService = new PasskeyVerificationService {
    override def buildCreationOptions(
        userId: UserId,
        userName: String
    ): Future[PublicKeyCredentialCreationOptions] =
      Future.failed(new UnsupportedOperationException)

    override def registerPasskey(
        userId: UserId,
        passkeyName: PasskeyName,
        creationResponse: JsValue
    ): Future[Unit] = Future.failed(new UnsupportedOperationException)

    override def listPasskeys(userId: UserId): Future[List[Passkey]] =
      Future.failed(new UnsupportedOperationException)

    override def deletePasskey(
        userId: UserId,
        passkeyId: PasskeyId
    ): Future[Unit] =
      Future.failed(new UnsupportedOperationException)

    override def buildAuthenticationOptions(
        userId: UserId
    ): Future[PublicKeyCredentialRequestOptions] =
      Future.failed(new UnsupportedOperationException)

    override def verifyPasskey(
        userId: UserId,
        authenticationResponse: JsValue
    ): Future[AuthenticationData] =
      Future.failed(new UnsupportedOperationException)
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

    "bypass authentication when disabled" in {
      val filter = new PasskeyAuthFilter(
        passkeyVerificationService = noOpVerificationService,
        passkeysEnabled = false,
        enablingCookieName = testCookieName
      )

      val request = createRequestWithCookie(validFormBody)
      val result = filter.filter(request).futureValue

      result.isEmpty shouldBe true
    }

    "bypass authentication when enabling cookie is not present" in {
      val filter = new PasskeyAuthFilter(
        passkeyVerificationService = noOpVerificationService,
        passkeysEnabled = true,
        enablingCookieName = testCookieName
      )

      val request = createRequestWithoutCookie(validFormBody)
      val result = filter.filter(request).futureValue

      result.isEmpty shouldBe true
    }
  }
}
