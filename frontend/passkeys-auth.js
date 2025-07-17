import {authenticatePasskey} from "./utils/passkeys.js";
import {getCsrfTokenFromMetaTag} from "./utils/csrf.js";

document.addEventListener("DOMContentLoaded", async () => {
  try {
    await authenticatePasskey(
      window.location.pathname + window.location.search,
      getCsrfTokenFromMetaTag(),
    );
  } catch (error) {
    console.error("Failed to authenticate passkey on gone page:", error);
  }
});
