package views.fragments

import com.gu.janus.model.{AwsAccount, AwsAccountAccess, Permission}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import play.api.test.Helpers.*

class MultiSelectHeroTest extends AnyFreeSpec with Matchers {

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

  "multiSelectHero template" - {
    "when passkeysEnabled is false" - {
      val html = views.html.fragments.multiSelectHero(passkeysEnabled = false)
      val renderedHtml = contentAsString(html)

      "should set data-passkey-protected to false" in {
        renderedHtml should include("""data-passkey-protected="false"""")
      }
    }

    "when passkeysEnabled is true" - {
      val html = views.html.fragments.multiSelectHero(passkeysEnabled = true)
      val renderedHtml = contentAsString(html)

      "should set data-passkey-protected to true" in {
        renderedHtml should include("""data-passkey-protected="true"""")
      }
    }
  }
}
