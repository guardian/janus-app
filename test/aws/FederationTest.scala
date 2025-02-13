package aws

import com.gu.janus.model.{AwsAccount, Permission}
import org.joda.time.DateTimeZone
import org.scalactic.source
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.routing.sird.QueryStringParameterExtractor
import play.api.test.Helpers.ALLOW
import software.amazon.awssdk.policybuilder.iam.{IamPolicy, IamStatement}
import testutils.JodaTimeUtils

import java.net.{URI, URLDecoder}

class FederationTest extends AnyFreeSpec with Matchers with JodaTimeUtils {
  import Federation._

  "duration" - {
    "when given a short-term permission" - {
      val permission = Permission(
        AwsAccount("test", "test"),
        "short-test",
        "Short Test Permission",
        IamPolicy
          .builder()
          .addStatement(IamStatement.builder().effect(ALLOW).build())
          .build(),
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
          18,
          30
        ) {
          duration(
            permission,
            None,
            Some(DateTimeZone.UTC)
          ) shouldEqual defaultShortTime
        }
      }
    }

    "when given a long-term permission" - {
      val permission = Permission(
        AwsAccount("test", "test"),
        "long-test",
        "Long Test Permission",
        IamPolicy
          .builder()
          .addStatement(IamStatement.builder().effect(ALLOW).build())
          .build()
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
          3,
          0
        ) {
          duration(
            permission,
            None,
            Some(DateTimeZone.UTC)
          ) shouldEqual defaultLongTime
        }

        "gives default time if we're after 19:00 local time" in withSystemTime(
          21,
          0
        ) {
          duration(
            permission,
            None,
            Some(DateTimeZone.UTC)
          ) shouldEqual defaultLongTime
        }

        "gives until 19:00 if we're within <max time> of 19:00 local time" in withSystemTime(
          10,
          0
        ) {
          duration(permission, None, Some(DateTimeZone.UTC)) shouldEqual 9.hours
        }

        "and no timezone is supplied, provides the default time, even near 19:00" in withSystemTime(
          15,
          0
        ) {
          duration(permission, None, None) shouldEqual defaultLongTime
        }

        "and we're quite near 19:00 with a TZ, give the remaining period" in withSystemTime(
          15,
          0
        ) {
          duration(permission, None, Some(DateTimeZone.UTC)) shouldEqual 4.hours
        }

        "and we're *very* near 19:00 with a TZ, give the remaining period" ignore withSystemTime(
          18,
          30
        ) {
          // do we need special logic near 19:00 so people don't get pointless perms?
          duration(permission, None, Some(DateTimeZone.UTC)) shouldEqual 4.hours
        }

        "uses the provided timezone to calculate the correct duration" in withSystemTime(
          15,
          0
        ) {
          duration(
            permission,
            None,
            Some(DateTimeZone.forOffsetHours(1))
          ) shouldEqual 3.hours
        }
      }
    }
  }

  "autoLogoutUrl" - {
    val signinUrl = "https://signin.aws.amazon.com/path/to/resource?foo=bar"

    "if autoLogout is enabled" - {
      "the returned URL is for the console logout endpoint" in {
        val url = autoLogoutUrl(signinUrl, autoLogout = true)
        url should startWith(
          "https://us-east-1.signin.aws.amazon.com/oauth?Action=logout&"
        )
      }

      "the provided URL is included (URL-encoded) in the redirect_uri GET parameter" - {
        "with its hostname changed to point to us-east-1" in {
          // Note: at time of writing the use of us-east-1 is required, so we enforce it here
          // https://serverfault.com/questions/985255/1097528#comment1469112_1097528
          val url = autoLogoutUrl(signinUrl, autoLogout = true)
          val redirectUri = extractRedirectUri(url)
          redirectUri should startWith(
            "https://us-east-1.signin.aws.amazon.com/"
          )
        }

        "and the rest of the URL unchanged" in {
          val url = autoLogoutUrl(signinUrl, autoLogout = true)
          val redirectUri = extractRedirectUri(url)
          redirectUri should endWith("/path/to/resource?foo=bar")
        }
      }
    }

    "returns the provided URL unchanged if autoLogout is not enabled" in {
      autoLogoutUrl(signinUrl, autoLogout = false) shouldEqual signinUrl
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

  // helper for testing the autoLogoutUrl functionality
  private val RedirectUri =
    QueryStringParameterExtractor.required("redirect_uri")
  private def extractRedirectUri(
      url: String
  )(implicit pos: source.Position): String = {
    new URI(url) match {
      case RedirectUri(redirectUri) => URLDecoder.decode(redirectUri, "UTF-8")
      case result =>
        fail(s"redirect_uri parameter not present on resulting URL $result")
    }
  }
}
