package integration

import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import com.microsoft.playwright._
import com.microsoft.playwright.options._

class PasskeyAuthTest extends AsyncFlatSpec with Matchers {

//  var playwright: Playwright = _
//  var browser: Browser = _
//  var context: BrowserContext = _
//  var page: Page = _
//
//  // This runs before each test
//  def withPlaywright(testCode: => Unit): Unit = {
//    try {
//      playwright = Playwright.create()
//      browser = playwright.chromium().launch(
//        new BrowserType.LaunchOptions()
//          .setHeadless(true)
//      )
//
//      // Create a browser context with virtual authenticator enabled
//      context = browser.newContext(
//        new Browser.NewContextOptions()
//          .setIgnoreHTTPSErrors(true)
//      )
//
//      // Enable WebAuthn testing
//      val cdpSession = context.asInstanceOf[BrowserContextImpl].createCDPSession()
//      cdpSession.send("WebAuthn.enable", new java.util.HashMap[String, Object]())
//
//      // Add a virtual authenticator
//      val authenticatorOptions = new java.util.HashMap[String, Object]()
//      authenticatorOptions.put("protocol", "ctap2")
//      authenticatorOptions.put("transport", "internal")
//      authenticatorOptions.put("hasResidentKey", true)
//      authenticatorOptions.put("hasUserVerification", true)
//      authenticatorOptions.put("isUserVerified", true)
//
//      val params = new java.util.HashMap[String, Object]()
//      params.put("options", authenticatorOptions)
//      cdpSession.send("WebAuthn.addVirtualAuthenticator", params)
//
//      page = context.newPage()
//
//      testCode
//    } finally {
//      if (page != null) page.close()
//      if (context != null) context.close()
//      if (browser != null) browser.close()
//      if (playwright != null) playwright.close()
//    }
//  }
//
//  "Passkey registration" should "complete successfully" in {
//    withPlaywright {
//      // Navigate to your registration page
//      page.navigate("https://your-app.com/register")
//
//      // Fill in registration form
//      page.fill("#email", "test@example.com")
//      page.fill("#name", "Test User")
//
//      // Trigger the passkey registration
//      page.click("#register-with-passkey")
//
//      // WebAuthn flow will be automatically handled by the virtual authenticator
//
//      // Wait for success message or redirect
//      page.waitForSelector("#registration-success", new Page.WaitForSelectorOptions().setTimeout(10000))
//
//      // Verify registration was successful
//      page.isVisible("#registration-success") shouldBe true
//    }
//
//    succeed
//  }
//
//  "Passkey authentication" should "login successfully" in {
//    withPlaywright {
//      // First register a passkey (could be extracted to a helper method)
//      page.navigate("https://your-app.com/register")
//      page.fill("#email", "test@example.com")
//      page.fill("#name", "Test User")
//      page.click("#register-with-passkey")
//      page.waitForSelector("#registration-success", new Page.WaitForSelectorOptions().setTimeout(10000))
//
//      // Now test the login flow
//      page.navigate("https://your-app.com/login")
//
//      // Trigger the passkey authentication
//      page.click("#login-with-passkey")
//
//      // Wait for login success
//      page.waitForNavigation(new Page.WaitForNavigationOptions().setUrl("**/dashboard"))
//
//      // Verify login was successful
//      page.url() should include("dashboard")
//    }
//
//    succeed
//  }
//
//  "Multiple passkeys" should "work with the correct one selected" in {
//    withPlaywright {
//      // Register first passkey
//      page.navigate("https://your-app.com/register")
//      page.fill("#email", "test1@example.com")
//      page.click("#register-with-passkey")
//      page.waitForSelector("#registration-success")
//
//      // Register second passkey
//      page.navigate("https://your-app.com/register")
//      page.fill("#email", "test2@example.com")
//      page.click("#register-with-passkey")
//      page.waitForSelector("#registration-success")
//
//      // Test login with selection
//      page.navigate("https://your-app.com/login")
//      page.click("#login-with-passkey")
//
//      // Virtual authenticator should handle credential selection automatically
//
//      page.waitForNavigation(new Page.WaitForNavigationOptions().setUrl("**/dashboard"))
//      page.url() should include("dashboard")
//    }
//
//    succeed
//  }
//
//  "Failed verification" should "show appropriate error" in {
//    withPlaywright {
//      // Setup a test where verification will fail
//      // This could involve manipulating the virtual authenticator state
//
//      page.navigate("https://your-app.com/login")
//      page.click("#login-with-passkey")
//
//      // Wait for error message
//      page.waitForSelector("#auth-error")
//      page.textContent("#auth-error") should include("verification failed")
//    }
//
//    succeed
//  }
}
