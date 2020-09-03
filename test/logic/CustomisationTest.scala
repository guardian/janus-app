package logic

import org.joda.time.{DateTimeZone, Duration}
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.test.FakeRequest


class CustomisationTest extends AnyFreeSpec with Matchers with OptionValues {
  import Customisation._

  "durationParams" - {
    "extracts duration from request if present" in {
      val request = FakeRequest("GET", "/test?duration=3600")
      val (duration, _) = durationParams(request)
      duration.value shouldEqual new Duration(3600)
    }

    "extracts duration as None if no parameter is available" in {
      val request = FakeRequest("GET", "/test")
      val (duration, _) = durationParams(request)
      duration shouldEqual None
    }

    "extracts duration as None if it is provided but empty" in {
      val request = FakeRequest("GET", "/test?duration=")
      val (duration, _) = durationParams(request)
      duration shouldEqual None
    }

    "extracts duration as None if an invalid duration is provided" in {
      val request = FakeRequest("GET", "/test?duration=abc")
      val (duration, _) = durationParams(request)
      duration shouldEqual None
    }

    "extracts timezone offset from request if present" in {
      val request = FakeRequest("GET", "/test?tzOffset=1")
      val (_, tzOffset) = durationParams(request)
      tzOffset.value shouldEqual DateTimeZone.forOffsetHours(1)
    }

    "extracts timezone as None if it is present but empty" in {
      val request = FakeRequest("GET", "/test?tzOffset=")
      val (_, tzOffset) = durationParams(request)
      tzOffset shouldEqual None
    }

    "extracts -ve timezone offset from request" in {
      val request = FakeRequest("GET", "/test?tzOffset=-4")
      val (_, tzOffset) = durationParams(request)
      tzOffset.value shouldEqual DateTimeZone.forOffsetHours(-4)
    }

    "extracts timezone offset as None if no parameter is available" in {
      val request = FakeRequest("GET", "/test")
      val (_, tzOffset) = durationParams(request)
      tzOffset shouldEqual None
    }

    "extracts timezone offset as None if an invalid duration is provided" in {
      val request = FakeRequest("GET", "/test?tzOffset=abc")
      val (_, tzOffset) = durationParams(request)
      tzOffset shouldEqual None
    }

    "extracts duration and timezone offset" in {
      val request = FakeRequest("GET", "/test?tzOffset=1&duration=3600")
      val (duration, tzOffset) = durationParams(request)
      duration.value shouldEqual new Duration(3600)
      tzOffset.value shouldEqual DateTimeZone.forOffsetHours(1)
    }
  }
}
