package integration

import integration.PlaywrightHelper.withPlaywright
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import com.microsoft.playwright.BrowserContext.StorageStateOptions
import java.nio.file.Paths

class GoogleAuthTest extends AnyFreeSpec with Matchers {

  private val domain = sys.env("JANUS_DOMAIN")

  "Successful Google auth" in withPlaywright { page =>
    // Load the saved authentication state
    val context = page.context()
    context.storageState(
      new StorageStateOptions().setPath(Paths.get(AuthSetup.statePath))
    )

    page.navigate(s"https://$domain/")

    // Wait for redirect back to Janus
    page.waitForURL(s"https://$domain/")

    page.title shouldBe "Your permissions - Janus"
  }
}
