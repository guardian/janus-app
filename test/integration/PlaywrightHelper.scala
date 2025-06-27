package integration

import com.google.gson.JsonObject
import com.microsoft.playwright.*

object PlaywrightHelper {

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
}
