import { getCsrfTokenFromMetaTag } from "./utils/csrf.js";
import { createAndSubmitForm } from "./utils/formUtils.js";
import { authenticatePasskey } from "./utils/passkeys.js";

document.addEventListener("DOMContentLoaded", async () => {
  try {
    const passkeysEnabled = !!document.querySelector(
      "[data-passkeys-enabled='true']",
    );
    const targetUrl = window.location.pathname + window.location.search;
    const csrfToken = getCsrfTokenFromMetaTag();
    if (!csrfToken) {
      console.error("CSRF token is missing. Aborting operation.");
      return;
    }

    if (passkeysEnabled) {
      await authenticatePasskey(targetUrl, csrfToken);
    } else {
      createAndSubmitForm(targetUrl, { csrfToken: csrfToken });
    }
  } catch (error) {
    console.error("Unexpected failure in authenticatePasskey:", error);
  }
});
