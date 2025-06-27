package integration

import integration.PlaywrightHelper.withPlaywright
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class PasskeysTest extends AnyFreeSpec with Matchers {

  private val domain = sys.env("JANUS_DOMAIN")

  "Passkeys" - {
    "Registration" - {
      "Successful first passkey creation" in withPlaywright { page =>
        page.navigate(s"https://$domain/user-account")
        page.click("#register-passkey")

        // WebAuthn flow will be automatically handled by the virtual authenticator

        // Handle modal dialog
        page.waitForSelector("input[type='text']")
        page.fill("input[type='text']", "t1")
        page.click("#submit-button")

        // Wait for redirect back to user account page
        page.waitForURL(s"https://$domain/user-account")

        page.isVisible("td[data-passkey-name='t1']") shouldBe true
      }
    }

    "Authentication" in withPlaywright { page =>
      page.navigate(s"https://$domain/passkey/mock-home")
      page.click("a[data-passkey-protected='true']")

      // TODO
      // page.waitForResponse("options")

      // WebAuthn flow will be automatically handled by the virtual authenticator

      // Wait for redirect back to user account page
      page.waitForURL(s"https://$domain/passkey/pretend-aws-console")

      page.url shouldBe s"https://$domain/passkey/pretend-aws-console"
    }
  }
}
