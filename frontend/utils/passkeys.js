import { createAndSubmitForm } from "./formUtils.js";
import { getPasskeyNameFromUser, showConfirmationModal } from "./modalUtils.js";
import { displayToast, messageType } from "./toastMessages.js";

const passkeyApi = {
  // Common function to handle fetch with CSRF token
  async fetchWithCsrf(
    url,
    method = "POST",
    csrfToken,
    contentType = "application/x-www-form-urlencoded",
    body = null,
  ) {
    const options = {
      method,
      headers: {
        "Content-Type": contentType,
        "CSRF-Token": csrfToken,
      },
      credentials: "same-origin",
    };

    if (body) {
      options.body = body;
    }

    return fetch(url, options);
  },

  /**
   * Common error handler for WebAuthn credential operations (get/create)
   * @param {Error} err - The error to handle
   * @param {string} operation - The operation being performed (e.g., "authentication", "creation")
   * @returns {boolean} true if the error was handled and execution should stop, false if it should be re-thrown
   */
  handleCredentialError(err, operation) {
    console.error(`Error during ${operation}:`, err);

    if (err.name === "AbortError") {
      displayToast(
        `${operation.charAt(0).toUpperCase() + operation.slice(1)} was aborted`,
        messageType.error,
      );
      return true;
    } else if (err.name === "NotAllowedError") {
      displayToast(
        `${operation.charAt(0).toUpperCase() + operation.slice(1)} was cancelled or not allowed`,
        messageType.warning,
      );
      return true;
    } else if (err.name === "InvalidStateError") {
      console.warn("Passkey already registered:", err);
      displayToast(
        "This passkey has already been registered.",
        messageType.warning,
      );
      return true;
    }

    // For unknown errors, return false to indicate they should be re-thrown or handled differently
    return false;
  },

  async getRegistrationOptions(
    csrfToken,
    endpoint = "/passkey/registration-options",
  ) {
    try {
      const response = await this.fetchWithCsrf(endpoint, "POST", csrfToken);
      const optionsJson = await response.json();

      if (!response.ok) {
        console.error("Registration options request failed:", optionsJson);
        displayToast(optionsJson.message, messageType.error);
        return null;
      }

      /*
       * authenticatorSelection.authenticatorAttachment can be "platform" | "cross-platform" | null.
       * A null value means any authenticator is allowed.
       * Unfortunately, even though null is the recommended value, Safari won't allow it
       * and Firefox gives a warning about it.
       * So we remove the field if its value is null and all browsers are then happy.
       * The effect is unchanged.
       *
       * This is a temporary measure until browsers accept the null value.
       */
      if (
        optionsJson.authenticatorSelection?.authenticatorAttachment === null
      ) {
        delete optionsJson.authenticatorSelection.authenticatorAttachment;
      }

      return optionsJson;
    } catch (error) {
      console.error("Error fetching registration options:", error);
      displayToast(
        "Network error while getting registration options",
        messageType.error,
      );
      return null;
    }
  },

  async getAuthenticationOptions(
    csrfToken,
    endpoint = "/passkey/auth-options",
  ) {
    try {
      const response = await this.fetchWithCsrf(endpoint, "POST", csrfToken);
      const optionsJson = await response.json();

      if (!response.ok) {
        if (response.status === 400) {
          /*
           * We only get a 400 response here if the user has no registered passkeys.
           * In this scenario, we redirect them to the user account page where they are urged to register one.
           */
          window.location.href = "/user-account";
          return;
        }

        console.error("Authentication options request failed:", optionsJson);
        displayToast(
          "Failed to get authentication options from server. Please try again.",
          messageType.warning,
        );
        return null;
      }

      return optionsJson;
    } catch (error) {
      console.error("Error fetching authentication options:", error);
      displayToast(
        "Network error while getting authentication options",
        messageType.error,
      );
      return null;
    }
  },

  async getOptionalAuthenticationOptions(
    csrfToken,
    endpoint = "/passkey/registration-auth-options",
  ) {
    try {
      const response = await this.fetchWithCsrf(endpoint, "POST", csrfToken);
      const optionsJson = await response.json();

      if (!response.ok) {
        console.error("Authentication options request failed:", optionsJson);
        displayToast(optionsJson.message, messageType.error);
        return null;
      }

      return optionsJson;
    } catch (error) {
      console.error("Error fetching authentication options:", error);
      displayToast(
        "Network error while getting authentication options",
        messageType.error,
      );
      return null;
    }
  },

  async getUserCredential(authOptionsJson, useConditionalUI = true) {
    const credentialGetOptions =
      PublicKeyCredential.parseRequestOptionsFromJSON(authOptionsJson);

    // First, try conditional mediation if requested (for autofill UI)
    if (useConditionalUI) {
      const abortController = new AbortController();

      const options = {
        publicKey: credentialGetOptions,
        mediation: "conditional",
        signal: abortController.signal,
      };

      try {
        console.debug(
          "Calling navigator.credentials.get with conditional mediation...",
        );

        // Race between conditional UI and a timeout
        const timeoutMs = 500;
        const conditionalPromise = navigator.credentials.get(options);
        const timeoutPromise = new Promise((_, reject) =>
          setTimeout(() => {
            abortController.abort();
            reject(new Error("ConditionalUITimeout"));
          }, timeoutMs),
        );

        const result = await Promise.race([conditionalPromise, timeoutPromise]);
        console.debug("Conditional mediation returned:", !!result);
        return result;
      } catch (err) {
        if (err.message === "ConditionalUITimeout") {
          console.debug("Conditional UI timed out, falling back to modal");
          // Fall through to modal UI below
        } else {
          console.error(
            "navigator.credentials.get (conditional) failed with:",
            err,
          );
          console.error("Error details:", {
            name: err.name,
            message: err.message,
            stack: err.stack,
          });
          throw err;
        }
      }
    }

    // Fall back to modal UI (or use it directly if useConditionalUI is false)
    const modalOptions = {
      publicKey: credentialGetOptions,
      mediation: "optional",
    };

    console.debug("Using modal UI for passkey authentication");

    try {
      console.debug("Calling navigator.credentials.get with modal UI...");
      const result = await navigator.credentials.get(modalOptions);
      console.debug("Modal UI returned:", !!result);
      return result;
    } catch (err) {
      console.error("navigator.credentials.get (modal) failed with:", err);
      console.error("Error details:", {
        name: err.name,
        message: err.message,
        stack: err.stack,
      });
      throw err;
    }
  },

  // Handle common error scenarios
  handlePasskeyError(err, operation) {
    if (err.name === "InvalidStateError") {
      console.warn("Passkey already registered:", err);
      displayToast(
        "This passkey has already been registered.",
        messageType.warning,
      );
    } else {
      console.error(`Error during passkey ${operation}:`, err);
      displayToast(`Passkey ${operation} failed`, messageType.error);
    }
  },
};

/**
 * Converts standard Base64 encoding to Base64URL encoding.
 * LastPass appears to give Base64 encoded credentials
 * but the WebAuthn spec requires Base64URL (no padding, - instead of +, _ instead of /).
 * See https://www.w3.org/TR/webauthn-3/#dom-publickeycredential-tojson
 *
 * @param {string} base64 - A base64 or base64url encoded string
 * @returns {string} A base64url encoded string
 */
function toBase64Url(base64) {
  if (!base64 || typeof base64 !== "string") {
    return base64;
  }
  return base64
    .replace(/\+/g, "-") // Replace + with -
    .replace(/\//g, "_") // Replace / with _
    .replace(/=+$/, ""); // Remove padding
}

/**
 * Recursively normalises all base64 strings in a credential object to base64url format.
 * This fixes issues where LastPass encodes in standard base64.
 * @param {Object} obj - The credential object to normalise
 * @returns {Object} The normalised credential object
 */
function normaliseCredentialJson(obj) {
  if (!obj || typeof obj !== "object") {
    return obj;
  }

  // Fields that should be base64url encoded according to WebAuthn spec
  const base64UrlFields = [
    "id",
    "rawId",
    "clientDataJSON",
    "attestationObject",
    "authenticatorData",
    "signature",
    "userHandle",
  ];

  if (Array.isArray(obj)) {
    return obj.map((item) => normaliseCredentialJson(item));
  }

  const normalised = {};
  for (const [key, value] of Object.entries(obj)) {
    if (base64UrlFields.includes(key) && typeof value === "string") {
      normalised[key] = toBase64Url(value);
    } else if (value && typeof value === "object") {
      normalised[key] = normaliseCredentialJson(value);
    } else {
      normalised[key] = value;
    }
  }
  return normalised;
}

/**
 * Registers a new passkey for the current user
 * @param {string} csrfToken - CSRF token for security verification
 * @returns {Promise<void>} A promise that resolves when registration completes
 */
export async function registerPasskey(csrfToken) {
  try {
    // 1. Fetch the authentication options
    const authOptionsJson =
      await passkeyApi.getOptionalAuthenticationOptions(csrfToken);
    if (!authOptionsJson) {
      return;
    }

    let existingCredential;

    // 2. If user has existing passkeys, use one to authenticate the registration of the new passkey
    if (authOptionsJson.allowCredentials.length > 0) {
      // Show confirmation modal
      const userConfirmed = await showConfirmationModal(
        "Authenticate with existing passkey",
        "Before you register a new passkey, use a passkey you have already registered to authenticate your request.",
        "authenticate",
      );

      if (!userConfirmed) {
        displayToast("Passkey registration cancelled", messageType.info);
        return;
      }

      try {
        // Now proceed with the authentication flow
        existingCredential =
          await passkeyApi.getUserCredential(authOptionsJson);
      } catch (err) {
        if (passkeyApi.handleCredentialError(err, "authentication")) {
          return; // Error was handled, exit the function
        }
        // Re-throw other errors to be handled by the outer catch block
        throw err;
      }

      await showConfirmationModal(
        "Register new passkey",
        "Authentication successful! Now register your new passkey.",
        "register",
      );
    }

    // 3. Fetch the passkey creation options
    const regOptionsJson = await passkeyApi.getRegistrationOptions(csrfToken);
    if (!regOptionsJson) {
      return;
    }

    // 4. Create a new passkey
    const credentialCreationOptions =
      PublicKeyCredential.parseCreationOptionsFromJSON(regOptionsJson);

    let createdCredential;
    try {
      createdCredential = await navigator.credentials.create({
        publicKey: credentialCreationOptions,
      });

      if (!createdCredential) {
        throw new Error("No credential was created");
      }

      console.debug("Passkey created successfully:", {
        id: createdCredential.id,
        type: createdCredential.type,
        hasResponse: !!createdCredential.response,
      });
    } catch (err) {
      if (passkeyApi.handleCredentialError(err, "creation")) {
        return; // Error was handled, exit the function
      }

      // If error was not handled, display a generic error message
      displayToast(
        `Failed to create passkey: ${err.message}`,
        messageType.error,
      );
      return;
    }

    // 5. Get name for the passkey
    const passkeyName = await getPasskeyNameFromUser();
    if (passkeyName === null) {
      return;
    }

    // 6. Make the registration call - includes authentication credentials if they exist so that they can be verified
    const formData = {
      passkey: JSON.stringify(
        normaliseCredentialJson(createdCredential.toJSON()),
      ),
      csrfToken: csrfToken,
      passkeyName: passkeyName,
    };

    if (existingCredential) {
      formData.credentials = JSON.stringify(
        normaliseCredentialJson(existingCredential.toJSON()),
      );
    }

    createAndSubmitForm("/passkey/register", formData);
  } catch (err) {
    passkeyApi.handlePasskeyError(err, "registration");
  }
}

/**
 * Authenticates a user with a passkey and redirects to the target URL
 * @param {string} targetHref - URL to redirect to after successful authentication
 * @param {string} csrfToken - CSRF token for security verification
 * @returns {Promise<void>} A promise that resolves when authentication completes
 */
export async function authenticatePasskey(targetHref, csrfToken) {
  try {
    const authOptionsJson = await passkeyApi.getAuthenticationOptions(
      csrfToken,
      "/passkey/auth-options",
    );
    if (!authOptionsJson) {
      return;
    }
    passkeyApi.getUserCredential(authOptionsJson).then((credential) => {
      if (credential) {
        createAndSubmitForm(targetHref, {
          credentials: JSON.stringify(
            normaliseCredentialJson(credential.toJSON()),
          ),
          csrfToken: csrfToken,
        });
      }
    });
  } catch (err) {
    passkeyApi.handlePasskeyError(err, "authentication");
  }
}

/**
 * Deletes a passkey from the user's account
 * @param {string} passkeyId - ID of the passkey to delete
 * @param {string} csrfToken - CSRF token for security verification
 * @returns {Promise<Object>} A promise that resolves with the JSON response data
 */
export async function deletePasskey(passkeyId, csrfToken) {
  try {
    // 1. Fetch the authentication options
    const authOptionsJson =
      await passkeyApi.getAuthenticationOptions(csrfToken);
    if (!authOptionsJson) {
      return;
    }

    // 2. Use a passkey to authenticate the deletion of the passkey
    const existingCredential =
      await passkeyApi.getUserCredential(authOptionsJson);

    // 3. Make the deletion call - includes authentication credentials so that they can be verified
    const response = await passkeyApi.fetchWithCsrf(
      `/passkey/${passkeyId}`,
      "DELETE",
      csrfToken,
      "text/plain",
      JSON.stringify(existingCredential.toJSON()),
    );

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      throw new Error(
        errorData.message || `Server returned ${response.status}`,
      );
    }

    return await response.json();
  } catch (error) {
    console.error("Error deleting passkey:", error);
    displayToast(`Error deleting passkey: ${error.message}`, messageType.error);
    throw error;
  }
}
