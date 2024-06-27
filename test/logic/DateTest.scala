package logic

import org.joda.time._
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class DateTest extends AnyFreeSpec with Matchers with OptionValues {
  "formatPeriod" - {
    "prints a nice message for a complex period" in {
      Date.formatPeriod(
        new Period(8, 10, 14, 0)
      ) shouldEqual "8 hours, 10 minutes, 14 seconds"
    }

    "prints a nice message for a period with only a few fields" in {
      Date.formatPeriod(
        new Period(8, 0, 14, 0)
      ) shouldEqual "8 hours, 14 seconds"
    }

    "can show a trivial interval" in {
      Date.formatPeriod(new Period(1, 0, 0, 0)) shouldEqual "1 hour"
    }
  }

  "formatDuration" - {
    "correctly formats a small duration" in {
      Date.formatDuration(new Duration(50 * 1000)) shouldEqual "50 seconds"
    }

    "correctly formats a large period" in {
      Date.formatDuration(
        new Duration(12 * 60 * 60 * 1000)
      ) shouldEqual "12 hours"
    }

    "correctly formats a complex period" in {
      Date.formatDuration(
        new Duration((3600 + 60 + 5) * 1000)
      ) shouldEqual "1 hour, 1 minute, 5 seconds"
    }
  }

  "firstDayOfWeek" - {
    "returns monday for the example date" in {
      val date = new DateTime(2015, 11, 6, 0, 0, 0, DateTimeZone.UTC)
      Date.firstDayOfWeek(date) shouldEqual new DateTime(
        2015,
        11,
        2,
        0,
        0,
        0,
        DateTimeZone.UTC
      )
    }

    "returns the same date when given a monday" in {
      val date = new DateTime(2015, 11, 9, 0, 0, 0, DateTimeZone.UTC)
      Date.firstDayOfWeek(date) shouldEqual new DateTime(
        2015,
        11,
        9,
        0,
        0,
        0,
        DateTimeZone.UTC
      )
    }
  }

  "parseDateStr" - {
    "should parse a nice date" in {
      Date
        .parseDateStr("2015-11-6")
        .value shouldEqual new DateTime(2015, 11, 6, 0, 0, 0, DateTimeZone.UTC)
    }

    "fails to parse junk" in {
      Date.parseDateStr("abc") shouldEqual None
    }
  }

  "weekAround" - {
    "gets the full week surrounding the given date" in {
      val date = new DateTime(2015, 11, 6, 0, 0, 0, DateTimeZone.UTC)
      Date.weekAround(date) shouldEqual (
        new DateTime(
          2015,
          11,
          2,
          0,
          0,
          0,
          DateTimeZone.UTC
        ),
        new DateTime(2015, 11, 9, 0, 0, 0, DateTimeZone.UTC)
      )
    }
  }

  "prevNextAuditWeeks" - {
    "returns the week before and after the given date" in {
      val date = new DateTime(2015, 11, 10, 0, 0, 0, DateTimeZone.UTC)
      val (Some(before), Some(after)) = Date.prevNextAuditWeeks(date)
      before shouldEqual new DateTime(2015, 11, 2, 0, 0, 0, DateTimeZone.UTC)
      after shouldEqual new DateTime(2015, 11, 16, 0, 0, 0, DateTimeZone.UTC)
    }

    "if previous week is before Janus audit logging began" - {
      val date = new DateTime(2015, 11, 5, 0, 0, 0, DateTimeZone.UTC)

      "excludes previous week" in {
        val (before, _) = Date.prevNextAuditWeeks(date)
        before shouldBe empty
      }

      "still includes the next week" in {
        val (_, after) = Date.prevNextAuditWeeks(date)
        after should not be empty
      }
    }

    "if next week is after the current date" - {
      val date = DateTime.now(DateTimeZone.UTC)
      "excludes the next week" in {
        val (_, after) = Date.prevNextAuditWeeks(date)
        after shouldBe empty
      }

      "still includes previous week" in {
        val (before, _) = Date.prevNextAuditWeeks(date)
        before should not be empty
      }
    }
  }

  "duration" - {
    val smallDuration = new Duration(1000)
    val largeDuration = new Duration(5000)

    "max" - {
      "returns the first duration if it is larger" in {
        Date.maxDuration(largeDuration, smallDuration) shouldEqual largeDuration
      }

      "returns the second duration if it is larger" in {
        Date.maxDuration(smallDuration, largeDuration) shouldEqual largeDuration
      }
    }

    "min" - {
      "returns the first duration if it is smaller" in {
        Date.minDuration(smallDuration, largeDuration) shouldEqual smallDuration
      }

      "returns the second duration if it is smaller" in {
        Date.minDuration(largeDuration, smallDuration) shouldEqual smallDuration
      }
    }
  }
}
