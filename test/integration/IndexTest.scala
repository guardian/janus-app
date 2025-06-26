package integration

import com.google.gson.JsonObject
import com.microsoft.playwright.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class IndexTest extends AnyFreeSpec with Matchers {

  private val domain = sys.env("JANUS_DOMAIN")

  def withPlaywright(testCode: Page => Unit): Unit = {
    val playwright = Playwright.create()
    val browser = playwright
      .chromium()
      .launch(
        new BrowserType.LaunchOptions()
          .setHeadless(false) /* Must be false to allow manual interaction */
      )
    val context = browser.newContext(
      new Browser.NewContextOptions()
        .setIgnoreHTTPSErrors(true)
    )
    val page = context.newPage()

    try {
      page.setDefaultTimeout(Integer.MAX_VALUE)

      // Add a virtual authenticator
      val authenticatorOptions = new JsonObject()
      authenticatorOptions.addProperty("protocol", "ctap2")
      authenticatorOptions.addProperty("transport", "internal")
      authenticatorOptions.addProperty("hasResidentKey", true)
      authenticatorOptions.addProperty("hasUserVerification", true)
      authenticatorOptions.addProperty("isUserVerified", true)

      val params = new JsonObject()
      params.add("options", authenticatorOptions)

      // Enable WebAuthn testing
      val cdpSession = context.newCDPSession(page)
      cdpSession.send("WebAuthn.enable", new JsonObject())
      cdpSession.send("WebAuthn.addVirtualAuthenticator", params)

      testCode(page)
    } finally {
      if (page != null) page.close()
      if (context != null) context.close()
      if (browser != null) browser.close()
      if (playwright != null) playwright.close()
    }
  }

  "Homepage should load successfully and allow manual login" in withPlaywright {
    page =>
      page.navigate(s"https://$domain/")
      page.waitForLoadState()

      // Pause for manual login
      println("\n\n==================================================")
      println("TEST PAUSED: Please complete the Google login manually")
      println("==================================================\n\n")

      // Wait for redirect back to Janus
      page.waitForURL(s"https://$domain/**", new Page.WaitForURLOptions())

      val title = page.title()
      title shouldBe "Your permissions - Janus"
  }

  "Registration" - {
    "Successful first passkey" - withPlaywright { page =>
      page.navigate(s"https://$domain/user-account")
      page.click("#register-passkey")

      // WebAuthn flow will be automatically handled by the virtual authenticator

      // Wait for redirect back to user account page
      page.waitForURL(
        s"https://$domain/user-account",
        new Page.WaitForURLOptions()
      )

      "Passkey created" in {
        page.isVisible("Mac") shouldBe true
      }
    }
  }
}
