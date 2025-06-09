package models

import com.webauthn4j.data.attestation.authenticator.AAGUID
import models.PasskeyAuthenticator.given_Reads_AAGUID
import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{JsError, Json}

class PasskeyAuthenticatorTest
    extends AnyFreeSpec
    with Matchers
    with EitherValues {

  "PasskeyAuthenticator" - {

    "should be deserializable from JSON with both description and icon" - {
      val jsonString =
        """{"description":"Test Passkey Authenticator","icon":"test-icon.png"}"""

      val parsed = Json.parse(jsonString)
      val result = Json.fromJson[PasskeyAuthenticator](parsed)

      "result should be successful" in {
        result.isSuccess shouldBe true
      }

      "description should be parsed correctly" in {
        val passkeyAuth = result.get
        passkeyAuth.description shouldEqual "Test Passkey Authenticator"
      }

      "icon should be parsed correctly" in {
        val passkeyAuth = result.get
        passkeyAuth.icon shouldEqual Some("test-icon.png")
      }
    }

    "should be deserializable from JSON with only description field" - {
      val jsonString = """{"description":"Test Passkey Authenticator"}"""

      val parsed = Json.parse(jsonString)
      val result = Json.fromJson[PasskeyAuthenticator](parsed)

      "result should be successful" in {
        result.isSuccess shouldBe true
      }

      "description should be parsed correctly" in {
        val passkeyAuth = result.get
        passkeyAuth.description shouldEqual "Test Passkey Authenticator"
      }

      "icon should be None" in {
        val passkeyAuth = result.get
        passkeyAuth.icon shouldEqual None
      }
    }

    "should fail deserialization when description is not a string" in {
      val invalidJson = """{"description":123,"icon":"test-icon.png"}"""

      val parsed = Json.parse(invalidJson)
      val result = Json.fromJson[PasskeyAuthenticator](parsed)

      result.isError shouldBe true
    }

    "should fail deserialization when icon is not a string" in {
      val invalidJson = """{"description":"Test Authenticator","icon":123}"""

      val parsed = Json.parse(invalidJson)
      val result = Json.fromJson[PasskeyAuthenticator](parsed)

      result.isError shouldBe true
    }

    "should fail deserialization when description field is missing" - {
      val incompleteJson = """{"icon":"test-icon.png"}"""

      val parsed = Json.parse(incompleteJson)
      val result = Json.fromJson[PasskeyAuthenticator](parsed)

      "result should be an error" in {
        result.isError shouldBe true
      }

      "error should mention description field" in {
        val errors = result.asInstanceOf[JsError].errors
        errors.flatMap(_._2).map(_.message).mkString should include(
          "missing"
        )
      }
    }

    "should handle null icon field as None" - {
      val jsonString = """{"description":"Test Authenticator","icon":null}"""

      val parsed = Json.parse(jsonString)
      val result = Json.fromJson[PasskeyAuthenticator](parsed)

      "result should be successful" in {
        result.isSuccess shouldBe true
      }

      "description should be parsed correctly" in {
        val passkeyAuth = result.get
        passkeyAuth.description shouldEqual "Test Authenticator"
      }

      "icon should be None" in {
        val passkeyAuth = result.get
        passkeyAuth.icon shouldEqual None
      }
    }
  }

  "fromResource" - {

    "should successfully load authenticators from valid JSON resource" - {
      val result =
        PasskeyAuthenticator.fromResource("passkeys_aaguid_descriptions.json")

      "result should not be empty" in {
        result should not be empty
      }

      "result should have positive size" in {
        // Since we're using the actual config file, just verify it returns a non-empty map
        result.size should be > 0
      }
    }

    "should return empty map when resource file does not exist" in {
      val result = PasskeyAuthenticator.fromResource("non-existent-file.json")

      result shouldBe empty
    }

    "should properly parse AAGUID keys as AAGUID objects" - {
      val result =
        PasskeyAuthenticator.fromResource("passkeys_aaguid_descriptions.json")

      "first key should be an AAGUID" in {
        if (result.nonEmpty) {
          val firstKey = result.keys.head
          firstKey shouldBe a[AAGUID]
        }
      }

      "first key should match AAGUID format" in {
        if (result.nonEmpty) {
          val firstKey = result.keys.head
          firstKey.toString should fullyMatch regex "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        }
      }
    }

    "should return PasskeyAuthenticator objects with correct fields" - {
      val result =
        PasskeyAuthenticator.fromResource("passkeys_aaguid_descriptions.json")

      "first auth should be a PasskeyAuthenticator" in {
        if (result.nonEmpty) {
          val firstAuth = result.values.head
          firstAuth shouldBe a[PasskeyAuthenticator]
        }
      }

      "first auth should have non-empty description" in {
        if (result.nonEmpty) {
          val firstAuth = result.values.head
          firstAuth.description should not be empty
        }
      }

      "first auth should have Option[String] icon field" in {
        if (result.nonEmpty) {
          val firstAuth = result.values.head
          // icon field should be Option[String]
          firstAuth.icon shouldBe a[Option[_]]
        }
      }
    }

    "should handle authenticators without icon field" - {
      val result =
        PasskeyAuthenticator.fromResource("passkeys_aaguid_descriptions.json")

      "authenticator without icon should have non-empty description" in {
        if (result.nonEmpty) {
          // Look for an authenticator without an icon
          val authsWithoutIcon = result.values.filter(_.icon.isEmpty)
          if (authsWithoutIcon.nonEmpty) {
            val authWithoutIcon = authsWithoutIcon.head
            authWithoutIcon.description should not be empty
          }
        }
      }

      "authenticator without icon should have None for icon" in {
        if (result.nonEmpty) {
          // Look for an authenticator without an icon
          val authsWithoutIcon = result.values.filter(_.icon.isEmpty)
          if (authsWithoutIcon.nonEmpty) {
            val authWithoutIcon = authsWithoutIcon.head
            authWithoutIcon.icon shouldBe None
          }
        }
      }
    }

    "should handle authenticators with icon field" - {
      val result =
        PasskeyAuthenticator.fromResource("passkeys_aaguid_descriptions.json")

      "authenticator with icon should have non-empty description" in {
        if (result.nonEmpty) {
          // Look for an authenticator with an icon
          val authsWithIcon = result.values.filter(_.icon.isDefined)
          if (authsWithIcon.nonEmpty) {
            val authWithIcon = authsWithIcon.head
            authWithIcon.description should not be empty
          }
        }
      }

      "authenticator with icon should have defined icon" in {
        if (result.nonEmpty) {
          // Look for an authenticator with an icon
          val authsWithIcon = result.values.filter(_.icon.isDefined)
          if (authsWithIcon.nonEmpty) {
            val authWithIcon = authsWithIcon.head
            authWithIcon.icon shouldBe defined
          }
        }
      }

      "authenticator icon should not be empty" in {
        if (result.nonEmpty) {
          // Look for an authenticator with an icon
          val authsWithIcon = result.values.filter(_.icon.isDefined)
          if (authsWithIcon.nonEmpty) {
            val authWithIcon = authsWithIcon.head
            authWithIcon.icon.get should not be empty
          }
        }
      }
    }
  }

  "AAGUID JSON reader" - {

    "should successfully parse valid AAGUID string" - {
      val validAaguid = "01020304-0506-0708-0901-020304050607"
      val json = Json.toJson(validAaguid)
      val result = Json.fromJson[AAGUID](json)

      "result should be successful" in {
        result.isSuccess shouldBe true
      }

      "parsed AAGUID should match original string" in {
        result.get.toString shouldEqual validAaguid
      }
    }

    "should fail to parse invalid AAGUID string" - {
      val invalidAaguid = "invalid-aaguid"
      val json = Json.toJson(invalidAaguid)
      val result = Json.fromJson[AAGUID](json)

      "result should be failure" in {
        result.isError shouldBe true
      }

      "result should have cause" in {
        val errors = result.asInstanceOf[JsError].errors
        errors.flatMap(_._2).map(_.message).mkString should include(
          "Invalid AAGUID format"
        )
      }
    }

    "should fail to parse non-string JSON" in {
      val json = Json.toJson(123)
      val result = Json.fromJson[AAGUID](json)

      result.isError shouldBe true
    }
  }
}
