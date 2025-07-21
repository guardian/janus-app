import { getCsrfTokenFromMetaTag } from "./utils/csrf.js";
import { authenticatePasskey } from "./utils/passkeys.js";

document.addEventListener("DOMContentLoaded", async () => {
  try {
    await authenticatePasskey(
      window.location.pathname + window.location.search,
      getCsrfTokenFromMetaTag(),
    );
  } catch (error) {
    console.error("Unexpected failure in authenticatePasskey:", error);
  }
});
