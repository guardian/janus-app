package logic

import com.gu.googleauth.UserIdentity
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.util.Base64UrlUtil
import io.circe.syntax.EncoderOps
import models.Passkey._
import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should

import java.nio.charset.StandardCharsets.UTF_8

class PasskeyTest extends AnyFreeSpec with should.Matchers with EitherValues {

  "registrationOptions" - {
    "creates valid registration options" in {
      val appName = "Test App"
      val appHost = "https://test.example.com"

      val testUser = UserIdentity(
        sub = "sub",
        email = "test.user@example.com",
        firstName = "Test",
        lastName = "User",
        exp = 0,
        avatarUrl = None
      )

      val options = Passkey.registrationOptions(
        appName = "Janus-Test",
        appName,
        appHost,
        testUser,
        challenge = new DefaultChallenge("challenge".getBytes(UTF_8))
      )
      options.toEither.value.asJson.spaces2 shouldBe
        """{
        |  "challenge" : "Y2hhbGxlbmdl",
        |  "rp" : {
        |    "id" : "test.example.com",
        |    "name" : "Janus-Test"
        |  },
        |  "user" : {
        |    "id" : "dGVzdC51c2Vy",
        |    "name" : "test.user",
        |    "displayName" : "Test User"
        |  },
        |  "pubKeyCredParams" : [
        |    {
        |      "type" : "public-key",
        |      "alg" : -7
        |    },
        |    {
        |      "type" : "public-key",
        |      "alg" : -257
        |    },
        |    {
        |      "type" : "public-key",
        |      "alg" : -8
        |    }
        |  ]
        |}""".stripMargin
    }
  }

  "verifiedRegistration" - {
    "rejects invalid registration response" in {
      val appHost = "https://test.example.com"
      val challenge = new DefaultChallenge("challenge".getBytes(UTF_8))
      val invalidJson = """{"type": "public-key", "id": "invalid"}"""

      val result = Passkey.verifiedRegistration(
        appHost,
        challenge,
        invalidJson
      )

      result.isFailure shouldBe true
    }
  }
}
