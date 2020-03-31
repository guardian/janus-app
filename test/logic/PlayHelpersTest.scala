package logic

import org.scalatest.{FreeSpec, Matchers}


class PlayHelpersTest extends FreeSpec with Matchers {
  import PlayHelpers._

  "splitQuerystringParam" - {
    "splits a querystring parameter into its parts" in {
      splitQuerystringParam("value1,value2") shouldEqual List("value1", "value2")
    }

    "works on a single value" in {
      splitQuerystringParam("value1") shouldEqual List("value1")
    }

    "does not fail with empty input" in {
      splitQuerystringParam("") shouldEqual Nil
    }
  }
}
