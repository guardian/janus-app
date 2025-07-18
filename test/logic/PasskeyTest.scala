package logic

import com.gu.googleauth.UserIdentity
import com.webauthn4j.data.attestation.authenticator.AAGUID
import com.webauthn4j.data.client.challenge.DefaultChallenge
import models.{JanusException, PasskeyEncodings, PasskeyMetadata}
import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant

class PasskeyTest extends AnyFreeSpec with should.Matchers with EitherValues {

  "registrationOptions" - {
    "creates valid registration options" in {
      val appHost = "https://test.example.com"

      val testUser = UserIdentity(
        sub = "sub",
        email = "a.solver@example.com",
        firstName = "A",
        lastName = "Solver",
        exp = 0,
        avatarUrl = None
      )

      val options = Passkey.registrationOptions(
        appName = "Janus-Test",
        appHost,
        testUser,
        challenge = new DefaultChallenge("challenge".getBytes(UTF_8)),
        existingPasskeys = Seq(
          PasskeyMetadata(
            id = "K9iphQ03JmTBqf-1pPGBXvpzfvt96ZAy51_BrKjibn0",
            name = "Test",
            registrationTime = Instant.parse("2025-05-21T09:30:00.000000Z"),
            aaguid = new AAGUID("adce0002-35bc-c60a-648b-0b25f1f05503"),
            lastUsedTime = None,
            authenticator = None
          )
        )
      )
      val json = PasskeyEncodings.mapper
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(options.toEither.value)
      json shouldBe
        """{
        |  "rp" : {
        |    "id" : "test.example.com",
        |    "name" : "Janus-Test"
        |  },
        |  "user" : {
        |    "id" : "YS5zb2x2ZXI",
        |    "name" : "a.solver",
        |    "displayName" : "A Solver"
        |  },
        |  "challenge" : "Y2hhbGxlbmdl",
        |  "pubKeyCredParams" : [ {
        |    "type" : "public-key",
        |    "alg" : -8
        |  }, {
        |    "type" : "public-key",
        |    "alg" : -7
        |  }, {
        |    "type" : "public-key",
        |    "alg" : -257
        |  } ],
        |  "timeout" : 60000,
        |  "excludeCredentials" : [ {
        |    "type" : "public-key",
        |    "id" : "K9iphQ03JmTBqf-1pPGBXvpzfvt96ZAy51_BrKjibn0"
        |  } ],
        |  "authenticatorSelection" : {
        |    "authenticatorAttachment" : null,
        |    "requireResidentKey" : false,
        |    "residentKey" : "discouraged",
        |    "userVerification" : "required"
        |  },
        |  "hints" : [ "client-device", "security-key", "hybrid" ],
        |  "attestation" : "direct",
        |  "extensions" : null
        |}""".stripMargin
    }
  }

  "verifiedRegistration" - {
    "rejects invalid registration response" - {
      val appHost = "https://test.example.com"
      val testUser = UserIdentity(
        sub = "sub",
        email = "test.user@example.com",
        firstName = "Test",
        lastName = "User",
        exp = 0,
        avatarUrl = None
      )
      val challenge = new DefaultChallenge("challenge".getBytes(UTF_8))
      val invalidJson = """{"type": "public-key", "id": "invalid"}"""

      val result = Passkey.verifiedRegistration(
        appHost,
        testUser,
        challenge,
        invalidJson
      )

      val exception = result.failed.get.asInstanceOf[JanusException]

      "returns failure" in {
        result.isFailure shouldBe true
      }

      "returns expected HTTP code" in {
        exception.httpCode shouldBe 400
      }

      "returns expected error message" in {
        exception.userMessage should include("Invalid passkey field")
      }
    }
  }

  "authenticationOptions" - {
    "creates valid authentication options when passkeys don't exist" in {
      val appHost = "https://test.example.com"
      val testUser = UserIdentity(
        sub = "sub",
        email = "test.user@example.com",
        firstName = "Test",
        lastName = "User",
        exp = 0,
        avatarUrl = None
      )
      val challenge = new DefaultChallenge("challenge".getBytes(UTF_8))
      val emptyPasskeys = Seq.empty[PasskeyMetadata]

      val options = Passkey.authenticationOptions(
        appHost,
        testUser,
        challenge,
        emptyPasskeys
      )
      val json = PasskeyEncodings.mapper
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(options.toEither.value)
      json shouldBe
        """{
          |  "challenge" : "Y2hhbGxlbmdl",
          |  "timeout" : 60000,
          |  "rpId" : "test.example.com",
          |  "allowCredentials" : [ ],
          |  "userVerification" : "required",
          |  "hints" : [ "client-device", "security-key", "hybrid" ],
          |  "extensions" : null
          |}""".stripMargin
    }

    "creates valid authentication options when passkeys exist" in {
      val appHost = "https://test.example.com"
      val testUser = UserIdentity(
        sub = "sub",
        email = "test.user@example.com",
        firstName = "Test",
        lastName = "User",
        exp = 0,
        avatarUrl = None
      )
      val challenge = new DefaultChallenge("challenge".getBytes(UTF_8))
      val existingPasskeys = Seq(
        PasskeyMetadata(
          id = "K9iphQ03JmTBqf-1pPGBXvpzfvt96ZAy51_BrKjibn0",
          name = "Test",
          registrationTime = Instant.parse("2025-05-21T09:30:00.000000Z"),
          aaguid = new AAGUID("adce0002-35bc-c60a-648b-0b25f1f05503"),
          lastUsedTime = None,
          authenticator = None
        )
      )

      val options = Passkey.authenticationOptions(
        appHost,
        testUser,
        challenge,
        existingPasskeys
      )
      val json = PasskeyEncodings.mapper
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(options.toEither.value)
      json shouldBe
        """{
          |  "challenge" : "Y2hhbGxlbmdl",
          |  "timeout" : 60000,
          |  "rpId" : "test.example.com",
          |  "allowCredentials" : [ {
          |    "type" : "public-key",
          |    "id" : "K9iphQ03JmTBqf-1pPGBXvpzfvt96ZAy51_BrKjibn0"
          |  } ],
          |  "userVerification" : "required",
          |  "hints" : [ "client-device", "security-key", "hybrid" ],
          |  "extensions" : null
          |}""".stripMargin
    }
  }
}
