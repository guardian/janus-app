import { displayToast, messageType } from "./toastMessages.js";

/**
 * Retrieves the CSRF token from the meta tag
 * @returns {string|null} The CSRF token or null if not found
 */
export function getCsrfTokenFromMetaTag() {
  const csrfToken = document.querySelector('meta[name="csrf-token"]')?.content;

  if (!csrfToken) {
    console.error("CSRF token not available");
    displayToast(
      "Security token not available. Please refresh the page.",
      messageType.error,
    );
    return null;
  }

  return csrfToken;
}
