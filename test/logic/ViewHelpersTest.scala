package logic

import fixtures.Fixtures._
import awscala.sts.TemporaryCredentials
import org.joda.time.DateTime
import org.scalatest.{FreeSpec, Matchers}
import ViewHelpers.shellCredentials


class ViewHelpersTest extends FreeSpec with Matchers {

  "shellCredentials" - {
    "for a single account" - {
      val creds = List(
        fooAct -> TemporaryCredentials("foo-key", "foo-secret", "foo-token", DateTime.now())
      )

      "includes provided key" in {
        shellCredentials(creds) should include ("aws_access_key_id foo-key")
      }

      "includes provided secret" in {
        shellCredentials(creds) should include ("aws_secret_access_key foo-secret")
      }

      "includes provided session token" in {
        shellCredentials(creds) should include ("aws_session_token foo-token")
      }

      "includes account name on each command" in {
        val lines = shellCredentials(creds).split('\n')
        all(lines) should include (s"--profile ${fooAct.authConfigKey}")
      }

      "puts leading space on all commands to exclude from bash history" in {
        val lines = shellCredentials(creds).split('\n')
        all(lines) should startWith (" ")
      }

      "all lines except the last should end with continuation (&& \\) so command pastes properly" in {
        val lines = shellCredentials(creds).split('\n')
        all(lines.init) should endWith ("&& \\")
      }
    }

    "for multiple accounts" - {
      val multiCreds = List(
        fooAct -> TemporaryCredentials("foo-key", "foo-secret", "foo-token", DateTime.now()),
        barAct -> TemporaryCredentials("bar-key", "bar-secret", "bar-token", DateTime.now())
      )

      "puts leading space on all commands to exclude from bash history" in {
        val lines = shellCredentials(multiCreds).split('\n')
        all(lines) should startWith (" ")
      }

      "all lines except the last should end with continuation (&& \\) so command pastes properly" in {
        val lines = shellCredentials(multiCreds).split('\n')
        all(lines.init) should endWith ("&& \\")
      }
    }
  }
}
