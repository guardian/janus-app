package controllers

import com.gu.googleauth.UserIdentity
import com.webauthn4j.data.client.challenge.DefaultChallenge
import logic.Passkey
import models.PasskeyMetadata
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant

class PasskeyAuthFilterIntegrationSpec extends AnyWordSpec with Matchers {

  val testUser = UserIdentity(
    "test@example.com",
    "Test User",
    "firstName",
    "lastName",
    12345L,
    None
  )

  "Passkey business logic integration" should {
    "create registration options with existing passkeys excluded" in {
      val appHost = "https://test.example.com"
      val appName = "Janus-Test"
      val challenge = new DefaultChallenge("challenge".getBytes(UTF_8))

      val existingPasskey = PasskeyMetadata(
        id = "existing-passkey-id",
        name = "My Phone",
        registrationTime = Instant.now(),
        aaguid = com.webauthn4j.data.attestation.authenticator.AAGUID.NULL,
        lastUsedTime = None
      )

      val result = Passkey.registrationOptions(
        appName,
        appHost,
        testUser,
        challenge,
        existingPasskeys = Seq(existingPasskey)
      )

      result.isSuccess shouldBe true
      val options = result.get
      options.getExcludeCredentials.size() shouldBe 1
    }

    "create authentication options successfully" in {
      val appHost = "https://test.example.com"

      val result = Passkey.authenticationOptions(appHost, testUser)

      result.isSuccess shouldBe true
      val options = result.get
      options.getTimeout shouldBe 10000L // 10 seconds default
    }

    "handle registration with invalid challenge" in {
      val appHost = "https://test.example.com"
      val challenge = new DefaultChallenge("invalid".getBytes(UTF_8))
      val validJsonButWrongChallenge = """{
        "type": "public-key",
        "id": "valid-id",
        "response": {
          "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIiwiY2hhbGxlbmdlIjoiZGlmZmVyZW50LWNoYWxsZW5nZSIsIm9yaWdpbiI6Imh0dHBzOi8vdGVzdC5leGFtcGxlLmNvbSJ9",
          "attestationObject": "invalid"
        }
      }"""

      val result = Passkey.verifiedRegistration(
        appHost,
        testUser,
        challenge,
        validJsonButWrongChallenge
      )

      result.isFailure shouldBe true
    }

    "handle authentication with malformed JSON" in {
      val malformedJson = """{"type": "public-key", "incomplete": true"""

      val result = Passkey.parsedAuthentication(testUser, malformedJson)

      result.isFailure shouldBe true
    }
  }

  "UserIdentity usage in business logic" should {
    "generate consistent user info for passkey operations" in {
      val appHost = "https://test.example.com"
      val appName = "Janus-Test"
      val challenge = new DefaultChallenge("test".getBytes(UTF_8))

      val registrationResult = Passkey.registrationOptions(
        appName,
        appHost,
        testUser,
        challenge,
        existingPasskeys = Seq.empty
      )

      val authenticationResult =
        Passkey.authenticationOptions(appHost, testUser)

      registrationResult.isSuccess shouldBe true
      authenticationResult.isSuccess shouldBe true

      // Both should use the same user information
      registrationResult.get.getUser.getName shouldBe testUser.username
      registrationResult.get.getUser.getDisplayName shouldBe testUser.fullName
    }

    "handle user with minimal information" in {
      val minimalUser = UserIdentity(
        sub = "minimal@example.com",
        email = "minimal@example.com",
        firstName = "",
        lastName = "",
        exp = 0L,
        avatarUrl = None
      )

      val result =
        Passkey.authenticationOptions("https://test.example.com", minimalUser)

      result.isSuccess shouldBe true
    }
  }
}
