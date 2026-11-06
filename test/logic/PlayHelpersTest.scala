package logic

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class PlayHelpersTest extends AnyFreeSpec with Matchers {
  import PlayHelpers._

  "splitQuerystringParam" - {
    "splits a querystring parameter into its parts" in {
      splitQuerystringParam("value1,value2") shouldEqual List(
        "value1",
        "value2"
      )
    }

    "works on a single value" in {
      splitQuerystringParam("value1") shouldEqual List("value1")
    }

    "does not fail with empty input" in {
      splitQuerystringParam("") shouldEqual Nil
    }
  }
}
