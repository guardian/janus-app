package integration

import com.microsoft.playwright.{Browser, BrowserType, Page, Playwright}
import com.microsoft.playwright.BrowserContext.StorageStateOptions

import java.nio.file.Paths

object AuthSetup {

  val statePath: String = sys.env("AUTH_STATE_PATH")
  private val domain = sys.env("JANUS_DOMAIN")

  @main
  def saveAuthState(): Unit = {
    val playwright = Playwright.create()
    val browser = playwright
      .chromium()
      .launch(
        new BrowserType.LaunchOptions()
          .setHeadless(false) /* Must be false to allow manual interaction */
      )
    val context = browser.newContext()
    val page = context.newPage()

    // Navigate and manually complete auth with 2FA
    page.navigate(s"https://$domain/")
    // Pause for manual authentication
    page.pause()
    // Save the authentication state
    context.storageState(
      new StorageStateOptions().setPath(Paths.get(statePath))
    )

    browser.close()
    playwright.close()
  }
}
