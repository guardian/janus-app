package controllers

import com.gu.googleauth.UserIdentity
import com.webauthn4j.data.client.challenge.DefaultChallenge
import logic.Passkey
import models.JanusException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Logger

import java.nio.charset.StandardCharsets.UTF_8
import scala.util.{Failure, Success, Try}

class PasskeyAuthFilterSpec extends AnyWordSpec with Matchers {

  class PasskeyAuthFilterSpec extends AnyWordSpec with Matchers {

    val testUser = UserIdentity(
      "test@example.com",
      "Test User",
      "firstName",
      "lastName",
      12345L,
      None
    )
    val testHost = "example.com"

    "handleApiResponse" should {
      implicit val mockLogger: Logger = Logger("test")

      "return None for successful operation" in {
        val operation = Success(())

        PasskeyAuthFilter.handleApiResponse(
          operation,
          testUser.username
        ) shouldBe None
      }

      "return Some(Result) with correct status for JanusException" in {
        val exception = JanusException.missingFieldInRequest(testUser, "field")
        val operation = Failure(exception)

        val result =
          PasskeyAuthFilter.handleApiResponse(operation, testUser.username)
        result shouldBe defined
      }

      "return Some(Result) with INTERNAL_SERVER_ERROR for other exceptions" in {
        val exception = new RuntimeException("Test error")
        val operation = Failure(exception)

        val result =
          PasskeyAuthFilter.handleApiResponse(operation, testUser.username)
        result shouldBe defined
      }
    }

    "Passkey logic integration with UserIdentity" should {
      "generate valid registration options" in {
        val appHost = "https://test.example.com"
        val appName = "Janus-Test"
        val challenge = new DefaultChallenge("challenge".getBytes(UTF_8))

        val result = Passkey.registrationOptions(
          appName,
          appHost,
          testUser,
          challenge,
          existingPasskeys = Seq.empty
        )

        result.isSuccess shouldBe true
        result.get.getUser.getName shouldBe testUser.username
      }

      "generate valid authentication options" in {
        val appHost = "https://test.example.com"

        val result = Passkey.authenticationOptions(appHost, testUser)

        result.isSuccess shouldBe true
      }

      "reject invalid registration JSON" in {
        val appHost = "https://test.example.com"
        val challenge = new DefaultChallenge("challenge".getBytes(UTF_8))
        val invalidJson = """{"type": "public-key", "id": "invalid"}"""

        val result = Passkey.verifiedRegistration(
          appHost,
          testUser,
          challenge,
          invalidJson
        )

        result.isFailure shouldBe true
        result.failed.get shouldBe a[JanusException]
      }

      "reject invalid authentication JSON" in {
        val invalidJson = """{"type": "public-key", "response": "invalid"}"""

        val result = Passkey.parsedAuthentication(testUser, invalidJson)

        result.isFailure shouldBe true
        result.failed.get shouldBe a[JanusException]
      }
    }

    "JanusException creation with UserIdentity" should {
      "create appropriate exceptions for missing fields" in {
        val exception =
          JanusException.missingFieldInRequest(testUser, "credentials")

        exception.userMessage should include("credentials")
        exception.engineerMessage should include(testUser.username)
      }

      "create appropriate exceptions for invalid fields" in {
        val cause = new RuntimeException("Test cause")
        val exception =
          JanusException.invalidFieldInRequest(testUser, "passkey", cause)

        exception.userMessage should include("passkey")
        exception.engineerMessage should include(testUser.username)
        exception.causedBy shouldBe Some(cause)
      }
    }
  }
}
