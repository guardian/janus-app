package logic

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.time.ZoneOffset.UTC
import java.time.{Duration, Instant, LocalDateTime, ZoneId, ZonedDateTime}

class DateTest extends AnyFreeSpec with Matchers with OptionValues {
  "formatDuration" - {
    "prints a nice message for a complex duration" in {
      Date.formatDuration(
        Duration.ofHours(8).plusMinutes(10).plusSeconds(14)
      ) shouldEqual "8 hours, 10 minutes, 14 seconds"
    }

    "prints a nice message for a duration with only a few fields" in {
      Date.formatDuration(
        Duration.ofHours(8).plusSeconds(14)
      ) shouldEqual "8 hours, 14 seconds"
    }

    "can show a trivial interval" in {
      Date.formatDuration(Duration.ofHours(1)) shouldEqual "1 hour"
    }

    "correctly formats a small duration" in {
      Date.formatDuration(Duration.ofSeconds(50)) shouldEqual "50 seconds"
    }

    "correctly formats a large period" in {
      Date.formatDuration(
        Duration.ofHours(12)
      ) shouldEqual "12 hours"
    }

    "correctly formats a complex period" in {
      Date.formatDuration(
        Duration.ofHours(1).plusMinutes(1).plusSeconds(5)
      ) shouldEqual "1 hour, 1 minute, 5 seconds"
    }
  }

  "firstDayOfWeek" - {
    "returns monday for the example date" in {
      val date =
        ZonedDateTime.of(2015, 11, 6, 0, 0, 0, 0, UTC).toInstant
      Date.firstDayOfWeek(date) shouldEqual ZonedDateTime
        .of(
          2015,
          11,
          2,
          0,
          0,
          0,
          0,
          UTC
        )
        .toInstant
    }

    "returns the same date when given a monday" in {
      val date = ZonedDateTime
        .of(LocalDateTime.of(2015, 11, 9, 0, 0), UTC)
        .toInstant
      Date.firstDayOfWeek(date) shouldEqual ZonedDateTime
        .of(
          2015,
          11,
          9,
          0,
          0,
          0,
          0,
          UTC
        )
        .toInstant
    }
  }

  "parseDateStr" - {
    "should parse a nice date" in {
      Date.parseDateStr("2015-11-06").value shouldEqual ZonedDateTime
        .of(
          LocalDateTime.of(2015, 11, 6, 0, 0),
          UTC
        )
        .toInstant
    }

    "fails to parse junk" in {
      Date.parseDateStr("abc") shouldEqual None
    }
  }

  "weekAround" - {
    "gets the full week surrounding the given date" in {
      val date = ZonedDateTime
        .of(LocalDateTime.of(2015, 11, 6, 0, 0), UTC)
        .toInstant
      Date.weekAround(date) shouldEqual (
        ZonedDateTime
          .of(LocalDateTime.of(2015, 11, 2, 0, 0), UTC)
          .toInstant,
        ZonedDateTime
          .of(LocalDateTime.of(2015, 11, 9, 0, 0), UTC)
          .toInstant
      )
    }
  }

  "prevNextAuditWeeks" - {
    "returns the week before and after the given date" in {
      val date =
        ZonedDateTime.of(2015, 11, 10, 0, 0, 0, 0, UTC).toInstant
      val (Some(before), Some(after)) = Date.prevNextAuditWeeks(date)
      before shouldEqual ZonedDateTime
        .of(2015, 11, 2, 0, 0, 0, 0, UTC)
        .toInstant
      after shouldEqual ZonedDateTime
        .of(2015, 11, 16, 0, 0, 0, 0, UTC)
        .toInstant
    }

    "if previous week is before Janus audit logging began" - {
      val date =
        ZonedDateTime.of(2015, 11, 5, 0, 0, 0, 0, UTC).toInstant

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
      val date = Instant.now()
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
    val smallDuration = Duration.ofSeconds(1000)
    val largeDuration = Duration.ofSeconds(5000)

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

  "displayMode" - {
    val timezone = ZoneId.of("Europe/London")

    "returns the correct display mode for Halloween" in {
      val today = ZonedDateTime.of(2025, 10, 31, 1, 0, 0, 0, timezone)
      Date.displayMode(today) shouldEqual models.Spooky
    }

    "returns the correct display mode for Christmas" in {
      val today = ZonedDateTime.of(2025, 12, 25, 2, 0, 0, 0, timezone)
      Date.displayMode(today) shouldEqual models.Festive
    }

    "returns the correct display mode for an arbitrary date neither Halloween nor Christmas" in {
      val today = ZonedDateTime.of(2025, 3, 5, 3, 0, 0, 0, timezone)
      Date.displayMode(today) shouldEqual models.Normal
    }
  }
}
