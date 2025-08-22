package filters

import com.gu.googleauth.AuthAction.UserIdentityRequest
import com.gu.googleauth.{AuthAction, UserIdentity}
import com.gu.playpasskeyauth.filters.PasskeyVerificationFilter
import com.gu.playpasskeyauth.services.PasskeyVerificationService
import com.gu.playpasskeyauth.web.RequestExtractor
import com.webauthn4j.credential.CredentialRecord
import com.webauthn4j.data.{
  AuthenticationData,
  PublicKeyCredentialCreationOptions,
  PublicKeyCredentialRequestOptions
}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.JsValue
import play.api.mvc.{AnyContentAsFormUrlEncoded, Cookie, Result, Results}
import play.api.test.{FakeHeaders, FakeRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ConditionalPasskeyVerificationFilterTest
    extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with Results {

  given RequestExtractor[UserIdentityRequest] =
    new RequestExtractor[UserIdentityRequest] {
      def findUserId[A](request: UserIdentityRequest[A]): Option[String] = ???

      def findCreationData[A](
          request: UserIdentityRequest[A]
      ): Option[JsValue] = ???

      def findAuthenticationData[A](
          request: UserIdentityRequest[A]
      ): Option[JsValue] = ???
    }

  private val testCookieName = "test-cookie"
  private val testUser = UserIdentity(
    sub = "test-sub-id",
    email = "test@example.com",
    firstName = "Test",
    lastName = "User",
    exp = Long.MaxValue,
    avatarUrl = None
  )

  private val verificationService = new PasskeyVerificationService {
    def creationOptions(
        userId: String
    ): Future[PublicKeyCredentialCreationOptions] = ???

    def register(
        userId: String,
        creationResponse: JsValue
    ): Future[CredentialRecord] = ???

    def authenticationOptions(
        userId: String
    ): Future[PublicKeyCredentialRequestOptions] = ???

    def verify(
        userId: String,
        authenticationResponse: JsValue
    ): Future[AuthenticationData] = ???
  }
  private val verificationFilter =
    new PasskeyVerificationFilter[AuthAction.UserIdentityRequest](
      verificationService
    ) {
      override def filter[A](
          request: UserIdentityRequest[A]
      ): Future[Option[Result]] = Future.successful(None)
    }

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

  "ConditionalPasskeyVerificationFilter" - {

    "bypass authentication when disabled" in {
      val filter = new ConditionalPasskeyVerificationFilter(
        passkeysEnabled = false,
        enablingCookieName = testCookieName,
        verificationFilter = verificationFilter
      )

      val request = createRequestWithCookie(validFormBody)
      val result = filter.filter(request).futureValue

      result shouldBe None
    }

    "bypass authentication when enabling cookie is not present" in {
      val filter = new ConditionalPasskeyVerificationFilter(
        passkeysEnabled = true,
        enablingCookieName = testCookieName,
        verificationFilter = verificationFilter
      )

      val request = createRequestWithoutCookie(validFormBody)
      val result = filter.filter(request).futureValue

      result shouldBe None
    }
  }
}
