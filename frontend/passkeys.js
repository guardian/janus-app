import { createAndSubmitForm } from './utils/formUtils.js';
import { getPasskeyNameFromUser } from './utils/modalUtils.js';
import { showConfirmationModal } from './utils/modalUtils.js';
import { displayToast, messageType } from './utils/toastMessages.js';

/**
 * Registers a new passkey for the current user
 * @param {string} csrfToken - CSRF token for security verification
 * @returns {Promise<void>} A promise that resolves when registration completes
 */
export async function registerPasskey(csrfToken) {
    try {
        // 1. Fetch the authentication options
        const authOptionsResponse = await fetch('/passkey/registration-auth-options', {
            method: 'POST',
            headers: {
                'CSRF-Token': csrfToken,
                'Content-Type': 'application/x-www-form-urlencoded',
            }
        });
        const authOptionsResponseJson = await authOptionsResponse.json();

        if (!authOptionsResponse.ok) {
            console.error('Authentication options request failed:', authOptionsResponseJson);
            displayToast('Failed to get authentication options from server. Please try again.', messageType.warning);
            return;
        }

        let existingCredential;

        // 2. If user has existing passkeys, use one to authenticate the registration of the new passkey
        if(authOptionsResponseJson.allowCredentials.length > 0) {
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
            const credentialGetOptions = PublicKeyCredential.parseRequestOptionsFromJSON(authOptionsResponseJson);
            existingCredential = await navigator.credentials.get({publicKey: credentialGetOptions});
            
            // Show modal instead of toast
            await showConfirmationModal(
                'Register new passkey',
                'Authentication successful! Now register your new passkey.',
                'register'                
            );
        }

        // 3. Fetch the passkey creation options
        const regOptionsResponse = await fetch('/passkey/registration-options', {
            method: 'POST',
            headers: {
                'CSRF-Token': csrfToken, // Securely include CSRF token in headers
                'Content-Type': 'application/x-www-form-urlencoded', // To satisfy Play CSRF filter
            }
        });
        const regOptionsResponseJson = await regOptionsResponse.json();

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
        if (regOptionsResponseJson.authenticatorSelection?.authenticatorAttachment === null) {
            delete regOptionsResponseJson.authenticatorSelection.authenticatorAttachment;
        }

        // 4. Create a new passkey
        const credentialCreationOptions = PublicKeyCredential.parseCreationOptionsFromJSON(regOptionsResponseJson);
        const createdCredential = await navigator.credentials.create({ publicKey: credentialCreationOptions });
        let passkeyName;
        try {
            passkeyName = await getPasskeyNameFromUser();
            if (passkeyName === null) {
                return;
            }
        } catch (error) {
            console.error('Modal error:', error);
            throw error;
        }

        // 5. Make the registration call - includes authentication credentials if they exist so that they can be verified
        if (existingCredential) {
            createAndSubmitForm('/passkey/register', {
                credentials: JSON.stringify(existingCredential.toJSON()),
                passkey: JSON.stringify(createdCredential.toJSON()),
                csrfToken: csrfToken,
                passkeyName: passkeyName
            });
        } else {
            createAndSubmitForm('/passkey/register', {
                passkey: JSON.stringify(createdCredential.toJSON()),
                csrfToken: csrfToken,
                passkeyName: passkeyName
            });
        }
    } catch (err) {
        if (err.name === 'InvalidStateError') {
            console.warn('Passkey already registered:', err);
            displayToast('This passkey has already been registered.', messageType.warning);
        } else {
            console.error('Error during passkey registration:', err);
            displayToast('Passkey not registered', messageType.error);
        }
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
        const authOptionsResponse = await fetch('/passkey/auth-options', {
            method: 'POST',
            headers: {
                'CSRF-Token': csrfToken, // Securely include CSRF token in headers,
                'Content-Type': 'application/x-www-form-urlencoded', // To satisfy Play CSRF filter
            }
        });
        const authOptionsResponseJson = await authOptionsResponse.json();

        if (!authOptionsResponse.ok) {
            console.error('Authentication options request failed:', authOptionsResponseJson);
            /*
             * We only get a 400 response here if the user has no registered passkeys.
             * In this scenario, we redirect them to the user account page where they are urged to register one.
             */
            if (authOptionsResponse.status === 400) {
                window.location.href = '/user-account';
                return;
            }
            displayToast('Failed to get authentication options from server. Please try again.', messageType.error);
            return;
        }

        const credentialGetOptions = PublicKeyCredential.parseRequestOptionsFromJSON(authOptionsResponseJson);
        const publicKeyCredential = await navigator.credentials.get({ publicKey: credentialGetOptions });

        createAndSubmitForm(targetHref, {
            credentials: JSON.stringify(publicKeyCredential.toJSON()),
            csrfToken: csrfToken
        });
    } catch (err) {
        console.error('Error during passkey authentication:', err);
        displayToast('Authentication failed. Please try again.', messageType.error)
    }
}

export async function bypassPasskeyAuthentication(targetHref, csrfToken) {
    try {
        createAndSubmitForm(targetHref, {
            csrfToken: csrfToken
        });
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
        const response = await fetch(`/passkey/${passkeyId}`, {
            method: 'DELETE',
            headers: {
                'CSRF-Token': csrfToken,
            },
            credentials: 'same-origin'
        });
        
        if (!response.ok) {
            let errorMessage = `Server returned ${response.status}`;
            try {
                const errorData = await response.json();
                if (errorData && errorData.message) {
                    errorMessage = errorData.message;
                }
            } catch (e) {
                console.warn('Could not parse error response as JSON:', e);
            }
            throw new Error(errorMessage);
        }
        
        const responseData = await response.json();
        
        return responseData;
    } catch (error) {
        console.error('Error deleting passkey:', error);
        displayToast(`Error deleting passkey: ${error.message}`, messageType.error);
        throw error;
    }
}   

