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

  async getUserCredential(authOptionsJson) {
    try {
      const authOptions =
        PublicKeyCredential.parseRequestOptionsFromJSON(authOptionsJson);

      /* LastPass binds its own code to the credentials get call,
       * which fails in certain cases.
       * So if auth options tell us that password manager browser extensions
       * shouldn't be enabled we fall back to the native call.
       */
      const extensionDetected = !!window.__nativeCredentialsGet;
      console.debug("Browser extension detected: ", extensionDetected);
      console.debug(
        "Enable password managers: ",
        authOptionsJson.enablePasswordManagers,
      );
      const credentialsGet =
        extensionDetected && !authOptionsJson.enablePasswordManagers
          ? window.__nativeCredentialsGet
          : navigator.credentials.get.bind(navigator.credentials);

      return await credentialsGet({
        publicKey: authOptions,
      });
    } catch (err) {
      if (err.name === "AbortError") {
        console.debug("Modal UI was aborted (possibly by autofill UI)");
        return null;
      } else if (err.name === "NotAllowedError") {
        console.debug(
          "Modal UI was cancelled by user or not allowed by browser",
        );
        return null;
      } else {
        console.error("navigator.credentials.get (modal) failed with:", err);
        throw err;
      }
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
 * LastPass and NordPass appear to give Base64 encoded credentials
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
 * This fixes issues where password managers encode in standard base64.
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
    } else if (value && typeof value === "function") {
      // Skip function properties as they cannot be serialized to JSON
    } else {
      normalised[key] = value;
    }
  }
  return normalised;
}

/**
 * Normalises the transports field to ensure it's an array of strings.
 * @param {Credential} credential - Credential data containing a transports element
 * @returns {Credential} Given credential with transports as an array of transport strings
 */
function normaliseTransports(credential) {
  const transports = credential.response.transports;
  // If transports is an object, extract its values
  if (typeof transports === "object") {
    credential.response.transports = Object.values(transports);
  }
  return credential;
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
    let createdCredential;

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
        if (!existingCredential) {
          console.debug("No existing credential: ", existingCredential);
          return;
        }
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
        normaliseCredentialJson(
          normaliseTransports(createdCredential.toJSON()),
        ),
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

    const credential = await passkeyApi.getUserCredential(authOptionsJson);

    if (credential) {
      createAndSubmitForm(targetHref, {
        credentials: JSON.stringify(
          normaliseCredentialJson(credential.toJSON()),
        ),
        csrfToken: csrfToken,
      });
    } else {
      console.error(
        "Passkey authentication cancelled or no credential selected",
      );
    }
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
      JSON.stringify(normaliseCredentialJson(existingCredential.toJSON())),
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
