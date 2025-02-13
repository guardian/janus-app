package aws

import com.gu.janus.model.{AwsAccount, Permission}
import com.gu.janus.policy.Iam.Policy
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import testutils.TimeUtils

import java.time.ZoneOffset
import java.time.ZoneOffset.UTC

class FederationTest extends AnyFreeSpec with Matchers with TimeUtils {
  import Federation._

  "duration" - {
    "when given a short-term permission" - {
      val permission = Permission(
        AwsAccount("test", "test"),
        "short-test",
        "Short Test Permission",
        Policy(Nil, None),
        shortTerm = true
      )

      "if a time is explicitly asked for" - {
        "grants the requested time if it is within the limit" in {
          duration(permission, Some(2.hours), None) shouldEqual 2.hours
        }

        "grants the max time if user requests a time longer than this" in {
          duration(permission, Some(24.hours), None) shouldEqual maxShortTime
        }

        "grants at least the minimum duration" in {
          duration(permission, Some(10.seconds), None) shouldEqual minShortTime
        }
      }

      "if no time is requested" - {
        "issues default short time" in {
          duration(permission, None, None) shouldEqual defaultShortTime
        }

        "issues default short time even near 19:00 with a timezone present" in withSystemTime(
          hour = 18,
          minute = 30,
          zoneId = Some(UTC)
        ) { clock =>
          duration(
            permission,
            None,
            clock
          ) shouldEqual defaultShortTime
        }
      }
    }

    "when given a long-term permission" - {
      val permission = Permission(
        AwsAccount("test", "test"),
        "long-test",
        "Long Test Permission",
        Policy(Nil, None),
        shortTerm = false
      )

      "if a time is explicitly asked for" - {
        "grants the requested time if it is provided and less than the maximum" in {
          duration(permission, Some(2.hours), None) shouldEqual 2.hours
        }

        "grants the max time if requested time is too long" in {
          duration(permission, Some(24.hours), None) shouldEqual maxLongTime
        }

        "grants at least the minimum number of seconds" in {
          duration(permission, Some(10.seconds), None) shouldEqual minLongTime
        }
      }

      "if no time is requested" - {
        "gives default time if we're a very long way from 19:00 local time" in withSystemTime(
          hour = 3,
          minute = 0,
          zoneId = Some(UTC)
        ) { clock =>
          duration(
            permission,
            None,
            clock
          ) shouldEqual defaultLongTime
        }

        "gives default time if we're after 19:00 local time" in withSystemTime(
          hour = 21,
          minute = 0,
          zoneId = Some(UTC)
        ) { clock =>
          duration(
            permission,
            None,
            clock
          ) shouldEqual defaultLongTime
        }

        "gives until 19:00 if we're within <max time> of 19:00 local time" in withSystemTime(
          hour = 10,
          minute = 0,
          zoneId = Some(UTC)
        ) { clock =>
          duration(permission, None, clock) shouldEqual 9.hours
        }

        "and no timezone is supplied, provides the default time, even near 19:00" in withSystemTime(
          hour = 15,
          minute = 0,
          zoneId = None
        ) { clock =>
          duration(permission, None, clock) shouldEqual defaultLongTime
        }

        "and we're quite near 19:00 with a TZ, give the remaining period" in withSystemTime(
          hour = 15,
          minute = 0,
          zoneId = Some(UTC)
        ) { clock =>
          duration(permission, None, clock) shouldEqual 4.hours
        }

        "and we're *very* near 19:00 with a TZ, give the remaining period" ignore withSystemTime(
          hour = 18,
          minute = 30,
          zoneId = Some(UTC)
        ) { clock =>
          // do we need special logic near 19:00 so people don't get pointless perms?
          duration(permission, None, clock) shouldEqual 4.hours
        }

        "uses the provided timezone to calculate the correct duration" in withSystemTime(
          hour = 15,
          minute = 0,
          zoneId = Some(ZoneOffset.ofHours(1))
        ) { clock =>
          duration(
            permission,
            None,
            clock
          ) shouldEqual 4.hours
        }
      }
    }
  }

  "getRoleName" - {
    "fetches role name from example" in {
      getRoleName(
        "arn:aws:iam::012345678910:role/test-role-name"
      ) shouldEqual "test-role-name"
    }

    "fetches role name when role is under a path" in {
      getRoleName(
        "arn:aws:iam::012345678910:role/path/role-name"
      ) shouldEqual "role-name"
    }
  }
}
