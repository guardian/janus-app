package integration

import com.microsoft.playwright._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import java.util.Scanner

class IndexTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private val domain = sys.env("JANUS_DOMAIN")

  private var playwright: Playwright = _
  private var browser: Browser = _
  private var context: BrowserContext = _
  private var page: Page = _

  override def beforeAll(): Unit = {
    playwright = Playwright.create()
    browser = playwright.chromium().launch(
      new BrowserType.LaunchOptions()
        .setHeadless(false) // Must be false to allow manual interaction
    )

    val contextOptions = new Browser.NewContextOptions()
      .setIgnoreHTTPSErrors(true)

    context = browser.newContext(contextOptions)
    page = context.newPage()
  }

  override def afterAll(): Unit = {
    if (context != null) context.close()
    if (browser != null) browser.close()
    if (playwright != null) playwright.close()
  }

  "Homepage" should "load successfully and allow manual login" in {
    page.navigate(s"https://$domain/")

    // Wait for page to load
    page.waitForLoadState()

    // Click on Google sign-in button (adjust the selector based on your UI)
    page.click("button:has-text('Sign in with Google')")

    // Pause for manual login
    println("\n\n==================================================")
    println("TEST PAUSED: Please complete the Google login manually")
    println("Press ENTER when you've completed the authentication")
    println("==================================================\n\n")

    new Scanner(System.in).nextLine() // Wait for user to press Enter

    println("Continuing test execution...")

    // Wait for redirect back to your application
    page.waitForURL(s"https://$domain/**", new Page.WaitForURLOptions().setTimeout(10000))

    // Verify we're logged in - adjust selectors based on your actual UI
    val loggedInElement = page.waitForSelector(".user-info", new Page.WaitForSelectorOptions().setTimeout(10000))
    loggedInElement.isVisible should be(true)

    // Additional assertions as needed
    page.title() should include("Dashboard") // Adjust based on your expected page title
  }
}
