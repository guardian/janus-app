package logic

import java.util.Base64

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc.Cookie

class FavouritesTest extends AnyFreeSpec with Matchers {
  import Favourites._

  "fromCookie" - {
    "parses empty favourites" in {
      fromCookie(testCookie(base64"[]")) shouldEqual Nil
    }

    "parses legacy favourites" - {
      "with no data" in {
        fromCookie(testCookie("[]")) shouldEqual Nil
      }

      "with data" in {
        fromCookie(
          testCookie("""["example", "another-example"]""")
        ) shouldEqual Nil
      }
    }

    "parses empty cookie value" in {
      fromCookie(testCookie("")) shouldEqual Nil
    }

    "returns no favourites from non-base64-encoded cookie value" in {
      fromCookie(testCookie("asdljkhasd")) shouldEqual Nil
    }

    "returns no favourites from invalid JSON cookie value" in {
      fromCookie(testCookie(base64"not Json")) shouldEqual Nil
    }

    "extracts a single favourite" in {
      fromCookie(testCookie(base64"""["test"]""")) shouldEqual List("test")
    }

    "extracts multiple favourites" in {
      fromCookie(testCookie(base64"""["foo", "bar"]""")) shouldEqual List(
        "foo",
        "bar"
      )
    }

    "returns empty favourites if there is no cookie present" in {
      fromCookie(None) shouldEqual Nil
    }
  }

  "toCookie" - {
    "creates a valid cookie with no favourites" in {
      toCookie(Nil).value shouldEqual base64"[]"
    }

    "creates a valid cookie" in {
      toCookie(List("foo", "bar")).value shouldEqual base64"""["foo","bar"]"""
    }
  }

  "toggleFavourites" - {
    "adds favourite to the end of the list if it does not already exist" in {
      toggleFavourite("bar", List("foo")) shouldEqual List("foo", "bar")
    }

    "removes favourite from the list if it already exists" in {
      toggleFavourite("foo", List("foo", "bar")) shouldEqual List("bar")
    }
  }

  private def testCookie(value: String): Cookie =
    Cookie("favourites", value)

  // keep tests readable by writing raw string and base encoding
  implicit class Base64Helper(val strC: StringContext) {
    def base64(args: Any*): String = {
      val str = strC.parts.mkString
      Base64.getEncoder.encodeToString(str.getBytes("UTF-8"))
    }
  }
}
