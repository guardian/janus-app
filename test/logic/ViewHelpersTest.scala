package logic

import com.gu.janus.model.Permission
import fixtures.Fixtures.*
import logic.ViewHelpers.{columnify, getColumn, shellCredentials}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.sts.model.Credentials

import java.time.Instant

class ViewHelpersTest extends AnyFreeSpec with Matchers {

  "shellCredentials" - {
    "for a single account" - {
      val fooCredential = Credentials
        .builder()
        .accessKeyId("foo-key")
        .secretAccessKey("foo-secret")
        .sessionToken("foo-token")
        .expiration(Instant.now())
        .build()
      val creds = List(
        fooDev -> fooCredential
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

      "includes credentials' profile name on each command" in {
        val lines = shellCredentials(creds).split('\n')
        all(lines) should include(s"--profile ${fooDev.credentialsProfile}")
      }

      "puts leading space on all commands to exclude from bash history" in {
        val lines = shellCredentials(creds).split('\n')
        all(lines) should startWith(" ")
      }

      "all lines except the last should end with continuation (&& \\) so command pastes properly" in {
        val lines = shellCredentials(creds).split('\n')
        all(lines.init) should endWith("&& \\")
      }

      "uses the correct profile name" - {
        "for an account-based permission" in {
          shellCredentials(creds) should include(s"--profile foo")
        }

        "for a permission that provides an overriden profile name" in {
          val creds = List(fooBespoke -> fooCredential)
          shellCredentials(creds) should include(
            s"--profile bespoke-permission"
          )
        }
      }
    }

    "for multiple accounts" - {
      val fooCredential = Credentials
        .builder()
        .accessKeyId("foo-key")
        .secretAccessKey("foo-secret")
        .sessionToken("foo-token")
        .expiration(Instant.now())
        .build()
      val barCredential = Credentials
        .builder()
        .accessKeyId("bar-key")
        .secretAccessKey("bar-secret")
        .sessionToken("bar-token")
        .expiration(Instant.now())
        .build()

      val multiCreds = List(
        fooDev -> fooCredential,
        barDev -> barCredential
      )

      "puts leading space on all commands to exclude from bash history" in {
        val lines = shellCredentials(multiCreds).split('\n')
        all(lines) should startWith(" ")
      }

      "all lines except the last should end with continuation (&& \\) so command pastes properly" in {
        val lines = shellCredentials(multiCreds).split('\n')
        all(lines.init) should endWith("&& \\")
      }

      "uses the correct profile name" - {
        "uses the account name for account-based permissions" in {
          shellCredentials(multiCreds) should include(s"--profile foo")
          shellCredentials(multiCreds) should include(s"--profile bar")
        }

        "does not use the account name for permissions that provide an overriden profile name" in {
          val creds = List(
            fooBespoke -> fooCredential,
            barBespoke -> barCredential
          )
          shellCredentials(creds) should (
            not include s"--profile foo" and not include s"--profile bar"
          )
        }

        "uses the specified profile for permissions that provide an overriden profile name" in {
          val creds = List(
            fooBespoke -> fooCredential,
            barBespoke -> barCredential
          )
          shellCredentials(creds) should (
            include(s"--profile bespoke-permission-f") and include(
              s"--profile bespoke-permission-b"
            )
          )
        }
      }
    }
  }

  "columnify" - {
    "returns the original list if 1 column is specified" in {
      val list = List(1, 2, 3, 4, 5)
      columnify(1, list) shouldEqual List(list)
    }

    "splits the list into 2 even columns" in {
      val list = List(1, 2, 3, 4, 5, 6)
      columnify(2, list) shouldEqual List(List(1, 3, 5), List(2, 4, 6))
    }

    "splits the list into 2 columns with a remainder" in {
      val list = List(1, 2, 3, 4, 5)
      columnify(2, list) shouldEqual List(List(1, 3, 5), List(2, 4))
    }

    "splits the list into 3 even columns" in {
      val list = List(1, 2, 3, 4, 5, 6, 7, 8, 9)
      columnify(3, list) shouldEqual List(
        List(1, 4, 7),
        List(2, 5, 8),
        List(3, 6, 9)
      )
    }

    "splits the list into 3 columns with a remainder" in {
      val list = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      columnify(3, list) shouldEqual List(
        List(1, 4, 7, 10),
        List(2, 5, 8),
        List(3, 6, 9)
      )
    }

    "splits the list into 3 columns with two remainders" in {
      val list = List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
      columnify(3, list) shouldEqual List(
        List(1, 4, 7, 10),
        List(2, 5, 8, 11),
        List(3, 6, 9)
      )
    }
  }

  "getColumn" - {
    "throws an exception if column count is less than 1" in {
      val list = List(1, 2, 3, 4, 5)
      assertThrows[AssertionError] {
        getColumn(0, list, 0)
      }
    }

    "throws an exception if column is less than 0" in {
      val list = List(1, 2, 3, 4, 5)
      assertThrows[AssertionError] {
        getColumn(1, list, -1)
      }
    }

    "throws an exception if column is greater than or equal to column count" in {
      val list = List(1, 2, 3, 4, 5)
      assertThrows[AssertionError] {
        getColumn(1, list, 1)
      }
    }

    "returns the column at the specified index" in {
      val list = List(1, 2, 3, 4, 5)
      getColumn(1, list, 0) shouldEqual List(1, 2, 3, 4, 5)
    }

    "returns the column at the specified index for a 2 column list" in {
      val list = List(1, 2, 3, 4, 5, 6)
      getColumn(2, list, 0) shouldEqual List(1, 3, 5)
    }

    "returns the column at the specified index for a 3 column list" in {
      val list = List(1, 2, 3, 4, 5, 6, 7, 8, 9)
      getColumn(3, list, 1) shouldEqual List(2, 5, 8)
    }
  }
}
