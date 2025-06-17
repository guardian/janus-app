package models

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.*

class JsonErrorFormatterTest extends AnyFreeSpec with Matchers {

  "formatJsErrors" - {
    "single validation error" - {
      val errors = Seq(
        JsPath() -> Seq(JsonValidationError("error.expected.string"))
      )
      val originalJson = """{"name": 123}"""
      val result = JsonErrorFormatter.formatJsErrors(errors, originalJson)

      "includes error count message" in {
        result should include("JSON validation failed with 1 error(s):")
      }

      "includes root path" in {
        result should include("Path: root")
      }

      "includes expected string message" in {
        result should include("Expected string")
      }
    }

    "multiple validation errors" - {
      val errors = Seq(
        JsPath() \ "name" -> Seq(JsonValidationError("error.expected.string")),
        JsPath() \ "age" -> Seq(JsonValidationError("error.expected.number"))
      )
      val originalJson = """{"name": 123, "age": "invalid"}"""
      val result = JsonErrorFormatter.formatJsErrors(errors, originalJson)

      "includes error count message" in {
        result should include("JSON validation failed with 2 error(s):")
      }

      "includes name path" in {
        result should include("Path: /name")
      }

      "includes expected string message" in {
        result should include("Expected string")
      }

      "includes age path" in {
        result should include("Path: /age")
      }

      "includes expected number message" in {
        result should include("Expected number")
      }
    }

    "nested path" - {
      val errors = Seq(
        JsPath() \ "user" \ "profile" \ "email" -> Seq(
          JsonValidationError("error.expected.string")
        )
      )
      val originalJson = """{"user": {"profile": {"email": 123}}}"""
      val result = JsonErrorFormatter.formatJsErrors(errors, originalJson)

      "includes correct nested path" in {
        result should include("Path: /user/profile/email")
      }

      "includes expected string message" in {
        result should include("Expected string")
      }
    }

    "multiple errors at same path" - {
      val errors = Seq(
        JsPath() \ "field" -> Seq(
          JsonValidationError("error.expected.string"),
          JsonValidationError("error.minLength", 5)
        )
      )
      val originalJson = """{"field": 123}"""
      val result = JsonErrorFormatter.formatJsErrors(errors, originalJson)

      "includes path" in {
        result should include("Path: /field")
      }

      "includes combined error messages" in {
        result should include("Expected string; error.minLength (5)")
      }
    }

    "context hint - includes context when field name found in JSON" in {
      val errors = Seq(
        JsPath() \ "username" -> Seq(
          JsonValidationError("error.expected.string")
        )
      )
      val originalJson = """{"username": 123, "email": "test@example.com"}"""

      val result = JsonErrorFormatter.formatJsErrors(errors, originalJson)

      result should include("Context: Check the structure for node 'username'")
    }

    "empty error sequence - includes error count message" in {
      val errors = Seq.empty[(JsPath, Seq[JsonValidationError])]
      val originalJson = """{"valid": "json"}"""

      val result = JsonErrorFormatter.formatJsErrors(errors, originalJson)

      result should include("JSON validation failed with 0 error(s):")
    }

    "empty JSON string" - {
      val errors = Seq(
        JsPath() \ "field" -> Seq(JsonValidationError("error.expected.string"))
      )
      val originalJson = ""
      val result = JsonErrorFormatter.formatJsErrors(errors, originalJson)

      "includes path" in {
        result should include("Path: /field")
      }

      "includes expected string message" in {
        result should include("Expected string")
      }

      "excludes context" in {
        result should not include ("Context:")
      }
    }
  }

  "integration tests" - {
    "real Play JSON validation" - {
      case class TestUser(name: String, age: Int, email: String)
      given testUserReads: Reads[TestUser] = Json.reads[TestUser]

      val invalidJson =
        """{"name": 123, "age": "invalid", "missing": "email"}"""
      val jsResult = Json.parse(invalidJson).validate[TestUser]

      "includes validation failed message" in {
        jsResult match {
          case JsError(errors) =>
            val formatted =
              JsonErrorFormatter.formatJsErrors(errors, invalidJson)
            formatted should include("JSON validation failed")
          case JsSuccess(_, _) =>
            fail("Expected validation to fail")
        }
      }

      "includes name path" in {
        jsResult match {
          case JsError(errors) =>
            val formatted =
              JsonErrorFormatter.formatJsErrors(errors, invalidJson)
            formatted should include("Path: /name")
          case JsSuccess(_, _) =>
            fail("Expected validation to fail")
        }
      }

      "includes age path" in {
        jsResult match {
          case JsError(errors) =>
            val formatted =
              JsonErrorFormatter.formatJsErrors(errors, invalidJson)
            formatted should include("Path: /age")
          case JsSuccess(_, _) =>
            fail("Expected validation to fail")
        }
      }

      "includes email path" in {
        jsResult match {
          case JsError(errors) =>
            val formatted =
              JsonErrorFormatter.formatJsErrors(errors, invalidJson)
            formatted should include("Path: /email")
          case JsSuccess(_, _) =>
            fail("Expected validation to fail")
        }
      }
    }

    "complex nested objects" - {
      case class Address(street: String, city: String, zipCode: Int)
      case class Person(name: String, age: Int, address: Address)

      given addressReads: Reads[Address] = Json.reads[Address]
      given personReads: Reads[Person] = Json.reads[Person]

      val invalidJson =
        """{"name": 123, "age": "invalid", "address": {"street": 456, "city": "Valid City"}}"""
      val jsResult = Json.parse(invalidJson).validate[Person]

      "includes name path" in {
        jsResult match {
          case JsError(errors) =>
            val formatted =
              JsonErrorFormatter.formatJsErrors(errors, invalidJson)
            formatted should include("Path: /name")
          case JsSuccess(_, _) =>
            fail("Expected validation to fail")
        }
      }

      "includes age path" in {
        jsResult match {
          case JsError(errors) =>
            val formatted =
              JsonErrorFormatter.formatJsErrors(errors, invalidJson)
            formatted should include("Path: /age")
          case JsSuccess(_, _) =>
            fail("Expected validation to fail")
        }
      }

      "includes address street path" in {
        jsResult match {
          case JsError(errors) =>
            val formatted =
              JsonErrorFormatter.formatJsErrors(errors, invalidJson)
            formatted should include("Path: /address/street")
          case JsSuccess(_, _) =>
            fail("Expected validation to fail")
        }
      }

      "includes address zipCode path" in {
        jsResult match {
          case JsError(errors) =>
            val formatted =
              JsonErrorFormatter.formatJsErrors(errors, invalidJson)
            formatted should include("Path: /address/zipCode")
          case JsSuccess(_, _) =>
            fail("Expected validation to fail")
        }
      }
    }

    "array validation" - {
      case class Item(id: Int, name: String)
      case class Container(items: Seq[Item])

      given itemReads: Reads[Item] = Json.reads[Item]
      given containerReads: Reads[Container] = Json.reads[Container]

      val invalidJson =
        """{"items": [{"id": "invalid", "name": "valid"}, {"id": 2}]}"""
      val jsResult = Json.parse(invalidJson).validate[Container]

      "includes validation failed message" in {
        jsResult match {
          case JsError(errors) =>
            val formatted =
              JsonErrorFormatter.formatJsErrors(errors, invalidJson)
            formatted should include("JSON validation failed")
          case JsSuccess(_, _) =>
            fail("Expected validation to fail")
        }
      }

      "includes path information" in {
        jsResult match {
          case JsError(errors) =>
            val formatted =
              JsonErrorFormatter.formatJsErrors(errors, invalidJson)
            // Array validation errors should be present
            formatted should include("Path:")
          case JsSuccess(_, _) =>
            fail("Expected validation to fail")
        }
      }
    }
  }
}
