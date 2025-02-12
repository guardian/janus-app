package logic

import fixtures.Fixtures._
import logic.ViewHelpers.shellCredentials
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.sts.model.Credentials

import java.time.Instant

class ViewHelpersTest extends AnyFreeSpec with Matchers {

  "shellCredentials" - {
    "for a single account" - {
      val creds = List(
        fooAct -> Credentials
          .builder()
          .accessKeyId("foo-key")
          .secretAccessKey("foo-secret")
          .sessionToken("foo-token")
          .expiration(Instant.now())
          .build()
      )

      "includes provided key" in {
        shellCredentials(creds) should include("aws_access_key_id foo-key")
      }

      "includes provided secret" in {
        shellCredentials(creds) should include(
          "aws_secret_access_key foo-secret"
        )
      }

      "includes provided session token" in {
        shellCredentials(creds) should include("aws_session_token foo-token")
      }

      "includes account name on each command" in {
        val lines = shellCredentials(creds).split('\n')
        all(lines) should include(s"--profile ${fooAct.authConfigKey}")
      }

      "puts leading space on all commands to exclude from bash history" in {
        val lines = shellCredentials(creds).split('\n')
        all(lines) should startWith(" ")
      }

      "all lines except the last should end with continuation (&& \\) so command pastes properly" in {
        val lines = shellCredentials(creds).split('\n')
        all(lines.init) should endWith("&& \\")
      }
    }

    "for multiple accounts" - {
      val multiCreds = List(
        fooAct -> Credentials
          .builder()
          .accessKeyId("foo-key")
          .secretAccessKey("foo-secret")
          .sessionToken("foo-token")
          .expiration(Instant.now())
          .build(),
        barAct -> Credentials
          .builder()
          .accessKeyId("bar-key")
          .secretAccessKey("bar-secret")
          .sessionToken("bar-token")
          .expiration(Instant.now())
          .build()
      )

      "puts leading space on all commands to exclude from bash history" in {
        val lines = shellCredentials(multiCreds).split('\n')
        all(lines) should startWith(" ")
      }

      "all lines except the last should end with continuation (&& \\) so command pastes properly" in {
        val lines = shellCredentials(multiCreds).split('\n')
        all(lines.init) should endWith("&& \\")
      }
    }
  }
}
