package views.fragments

import com.gu.janus.model.{AwsAccount, AwsAccountAccess, Permission}
import models.DisplayMode
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.test.Helpers._

class AwsAccountsTest extends AnyFreeSpec with Matchers {

  private val testAccount = AwsAccount("test-key", "Test Account")
  private val testPermission = Permission(
    account = testAccount,
    label = "Test Permission",
    description = "description",
    policy = None,
    managedPolicyArns = None,
    shortTerm = true
  )
  private val testAccountAccess =
    AwsAccountAccess(testAccount, List(testPermission), isFavourite = false)

  "awsAccounts template" - {
    "when passkeysEnabled is false" - {
      val html = views.html.fragments.awsAccounts(
        List(testAccountAccess),
        allowFavs = true,
        DisplayMode.Normal,
        passkeysEnabled = false
      )
      val renderedHtml = contentAsString(html)

      "should include data-passkey-bypassed attribute" in {
        renderedHtml should include("""data-passkey-bypassed="true"""")
      }
    }

    "when passkeysEnabled is true" - {
      val html = views.html.fragments.awsAccounts(
        List(testAccountAccess),
        allowFavs = true,
        DisplayMode.Normal,
        passkeysEnabled = true
      )
      val renderedHtml = contentAsString(html)

      "should not include data-passkey-bypassed attribute" in {
        renderedHtml should not include ("data-passkey-bypassed")
      }
    }
  }
}
