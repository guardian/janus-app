import { createAndSubmitForm } from './utils/formUtils.js';
import { getPasskeyNameFromUser } from './utils/modalUtils.js';
import { showConfirmationModal } from './utils/modalUtils.js';
import { displayToast, messageType } from './utils/toastMessages.js';

const passkeyApi = {

    // Common function to handle fetch with CSRF token
    async fetchWithCsrf(url, method = 'POST', body = null, csrfToken) {
        const options = {
            method,
            headers: {
                'CSRF-Token': csrfToken,
                'Content-Type': 'application/x-www-form-urlencoded', // To satisfy Play CSRF filter
            },
            credentials: 'same-origin'
        };

        if (body) {
            options.body = body;
        }

        return fetch(url, options);
    },

    async getAuthenticationOptions(csrfToken, endpoint = '/passkey/registration-auth-options') {
        try {
            const response = await this.fetchWithCsrf(endpoint, 'POST', null, csrfToken);
            const optionsJson = await response.json();

            if (!response.ok) {
                console.error('Authentication options request failed:', optionsJson);
                displayToast('Failed to get authentication options from server. Please try again.', messageType.warning);
                return null;
            }

            return optionsJson;
        } catch (error) {
            console.error('Error fetching authentication options:', error);
            displayToast('Network error while getting authentication options', messageType.error);
            return null;
        }
    },

    async getUserCredential(authOptionsJson) {
        const credentialGetOptions = PublicKeyCredential.parseRequestOptionsFromJSON(authOptionsJson);
        return await navigator.credentials.get({publicKey: credentialGetOptions});
    },

    // Handle common error scenarios
    handlePasskeyError(err, operation) {
        if (err.name === 'InvalidStateError') {
            console.warn('Passkey already registered:', err);
            displayToast('This passkey has already been registered.', messageType.warning);
        } else {
            console.error(`Error during passkey ${operation}:`, err);
            displayToast(`Passkey ${operation} failed`, messageType.error);
        }
    }
};

/**
 * Registers a new passkey for the current user
 * @param {string} csrfToken - CSRF token for security verification
 * @returns {Promise<void>} A promise that resolves when registration completes
 */
export async function registerPasskey(csrfToken) {
    try {
        const authOptionsJson = await passkeyApi.getAuthenticationOptions(csrfToken);
        if (!authOptionsJson) return;

        let existingCredential;

        // 2. If user has existing passkeys, use one to authenticate the registration of the new passkey
        if (authOptionsJson.allowCredentials.length > 0) {
            // Show confirmation modal
            const userConfirmed = await showConfirmationModal(
                'Authenticate with existing passkey',
                'Before you register a new passkey, use a passkey you have already registered to authenticate your request.',
                'authenticate'
            );

            if (!userConfirmed) {
                displayToast('Passkey registration cancelled', messageType.info);
                return;
            }

            // Now proceed with the authentication flow
            existingCredential = await passkeyApi.getUserCredential(authOptionsJson);

            await showConfirmationModal(
                'Register new passkey',
                'Authentication successful! Now register your new passkey.',
                'register'
            );
        }

        // 3. Fetch the passkey creation options
        const regOptionsResponse = await passkeyApi.fetchWithCsrf('/passkey/registration-options', 'POST', null, csrfToken);
        const regOptionsJson = await regOptionsResponse.json();

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
        if (regOptionsJson.authenticatorSelection?.authenticatorAttachment === null) {
            delete regOptionsJson.authenticatorSelection.authenticatorAttachment;
        }

        // 4. Create a new passkey
        const credentialCreationOptions = PublicKeyCredential.parseCreationOptionsFromJSON(regOptionsJson);
        const createdCredential = await navigator.credentials.create({publicKey: credentialCreationOptions});

        // 5. Get name for the passkey
        const passkeyName = await getPasskeyNameFromUser();
        if (passkeyName === null) return;

        // 6. Make the registration call - includes authentication credentials if they exist so that they can be verified
        const formData = {
            passkey: JSON.stringify(createdCredential.toJSON()),
            csrfToken: csrfToken,
            passkeyName: passkeyName
        };

        if (existingCredential) {
            formData.credentials = JSON.stringify(existingCredential.toJSON());
        }

        createAndSubmitForm('/passkey/register', formData);
    } catch (err) {
        passkeyApi.handlePasskeyError(err, 'registration');
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
        const authOptionsJson = await passkeyApi.getAuthenticationOptions(csrfToken, '/passkey/auth-options');
        if (!authOptionsJson) {
            /*
             * We only get a 400 response here if the user has no registered passkeys.
             * In this scenario, we redirect them to the user account page where they are urged to register one.
             */
            if (authOptionsJson.status === 400) {
                window.location.href = '/user-account';
            }
            return;
        }

        const publicKeyCredential = await passkeyApi.getUserCredential(authOptionsJson);

        createAndSubmitForm(targetHref, {
            credentials: JSON.stringify(publicKeyCredential.toJSON()),
            csrfToken: csrfToken
        });
    } catch (err) {
        passkeyApi.handlePasskeyError(err, 'authentication');
    }
}

export async function bypassPasskeyAuthentication(targetHref, csrfToken) {
    try {
        createAndSubmitForm(targetHref, {csrfToken});
    } catch (err) {
        console.error('Error during bypass of passkey authentication:', err);
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
        const authOptionsJson = await passkeyApi.getAuthenticationOptions(csrfToken);
        if (!authOptionsJson) return;

        // 2. Use a passkey to authenticate the deletion of the passkey
        const existingCredential = await passkeyApi.getUserCredential(authOptionsJson);

        // 3. Make the deletion call - includes authentication credentials so that they can be verified
        const response = await fetch(`/passkey/${passkeyId}`, {
            method: 'DELETE',
            headers: {'CSRF-Token': csrfToken},
            credentials: 'same-origin',
            body: JSON.stringify(existingCredential.toJSON())
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.message || `Server returned ${response.status}`);
        }

        return await response.json();
    } catch (error) {
        console.error('Error deleting passkey:', error);
        displayToast(`Error deleting passkey: ${error.message}`, messageType.error);
        throw error;
    }
}   
