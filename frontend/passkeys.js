import DOMPurify from 'dompurify';
import M from 'materialize-css';

/**
 * Registers a new passkey for the current user
 * @param {string} csrfToken - CSRF token for security verification
 * @returns {Promise<void>} A promise that resolves when registration completes
 */
export async function registerPasskey(csrfToken) {
    try {
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

        const credentialCreationOptions = PublicKeyCredential.parseCreationOptionsFromJSON(regOptionsResponseJson);
        const publicKeyCredential = await navigator.credentials.create({ publicKey: credentialCreationOptions });
        const passkeyName = await getPasskeyNameFromUser();

        createAndSubmitForm('/passkey/register', {
            passkey: JSON.stringify(publicKeyCredential.toJSON()),
            csrfToken: csrfToken,
            passkeyName: passkeyName
        });
    } catch (err) {
        if (err.name === 'InvalidStateError') {
            console.warn('Passkey already registered:', err);
            M.toast({html: 'This passkey has already been registered.', classes: 'rounded orange'});
        } else {
            console.error('Error during passkey registration:', err);
            M.toast({html: 'Registration failed: this passkey may have already been registered, or it could be a transient issue in which case please try again.', classes: 'rounded red'});
        }
    }
}

/**
 * Sets up click event listener for the passkey registration button
 * @param {string} selector - CSS selector for the register button
 */
export function setUpRegisterPasskeyButton(selector) {
    const registerButton = document.querySelector(selector);
    if (!registerButton) { return }

    registerButton?.addEventListener('click', function (e) {
        e.preventDefault();
        const csrfToken = this.getAttribute('csrf-token');
        registerPasskey(csrfToken).catch(function (err) {
            console.error('Error setting up register passkey button:', err);
        });
    });
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
            M.toast({
                html: 'Failed to get authentication options from server. Please try again.',
                classes: 'rounded red'
            });
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
        M.toast({ html: 'Authentication failed. Please try again.', classes: 'rounded red' });
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
        M.toast({ html: `Error deleting passkey: ${error.message}`, classes: 'rounded red' });
        throw error;
    }
}   

/**
 * Sets up click event listeners for passkey deletion buttons
 * @param {string} selector - CSS selector for delete buttons
 */
export function setUpDeletePasskeyButtons(selector) {
    const deleteButtons = document.querySelectorAll(selector);
    if (!deleteButtons.length) {
        return;
    }

    deleteButtons.forEach(button => {
        button.addEventListener('click', async () => {
            const passkeyName = button.getAttribute('data-passkey-name');
            const passkeyId = button.getAttribute('data-passkey-id');
            const csrfToken = button.getAttribute('csrf-token');
            
            if (!passkeyId) {
                console.error('No passkey ID found');
                M.toast({ html: 'Error: Passkey ID not found', classes: 'rounded red' });
                return;
            }
            
            if (!csrfToken) {
                console.error('No CSRF token found');
                M.toast({ html: 'Error: Security token not found', classes: 'rounded red' });
                return;
            }
            
            if (confirm(`Are you sure you want to delete the passkey "${passkeyName}"?`)) {
                try {
                    const result = await deletePasskey(passkeyId, csrfToken);
                    // Immediately redirect to the user-account page
                    // The flash message will be displayed after the redirect
                    if (result.redirect) {
                        window.location.href = result.redirect;
                    } else {
                        window.location.reload();
                    }
                } catch {
                    // Error is already handled in deletePasskey function
                }
            } else {
                M.toast({ html: 'Passkey deletion cancelled', classes: 'rounded orange' });
            }
        });
    });
}

/**
 * Creates and submits a form with the provided data
 * @param {string} targetHref - Form submission URL
 * @param {Object} formData - Data to include in the form
 */
function createAndSubmitForm(targetHref, formData) {
    const form = document.createElement('form');
    form.setAttribute('method', 'post');
    form.setAttribute('action', DOMPurify.sanitize(targetHref));

    Object.entries(formData).forEach(([name, value]) => {
        const input = document.createElement('input');
        input.setAttribute('type', 'hidden');
        input.setAttribute('name', DOMPurify.sanitize(name));
        input.setAttribute('value', DOMPurify.sanitize(value));
        form.appendChild(input);
    });

    document.body.append(form);
    form.submit();
}

/**
 * Sets up protected links that require passkey authentication
 * @param {NodeList|HTMLElement[]} links - Collection of link elements to protect
 */
export function setUpProtectedLinks(links) {
    if (!links.length) {
        return;
    }

    links.forEach((link) => {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            const csrfToken = link.getAttribute('csrf-token');
            const targetHref = link.href;
            if (link.dataset.passkeyBypassed) {
                bypassPasskeyAuthentication(targetHref, csrfToken).catch(function (err) {
                    console.error('Error setting up bypass of protected link:', err);
                });
            } else {
                authenticatePasskey(targetHref, csrfToken).catch(function (err) {
                    console.error('Error setting up protected link:', err);
                });
            }
        });
    });
}

/**
 * Displays flash messages from the server as toasts
 * @param {Object} flashMessages Object containing flash messages by type
 */
export function displayFlashMessages(flashMessages) {
    if (!flashMessages) { 
        return 
    }
    if (flashMessages.success) {
        M.toast({
            html: flashMessages.success,
            classes: 'green lighten-1 rounded',
        });
    }
    if (flashMessages.info) {
        M.toast({
            html: flashMessages.info,
            classes: 'blue lighten-1 rounded',
        });
    }
    if (flashMessages.error) {
        M.toast({
            html: flashMessages.error,
            classes: 'red lighten-1 rounded',
        });
    }
}

/**
 * Prompts the user to name a passkey via a modal dialog
 * @returns {Promise<string>} A promise that resolves with the passkey name
 */
function getPasskeyNameFromUser() {
    return new Promise((resolve, reject) => {        
        const modalElement = document.getElementById("passkey-name-modal");
        modalElement.style.visibility = "visible";
        // Initialize Materialize modal
        const modalInstance = M.Modal.init(modalElement, {
            dismissible: false, // User must use buttons to close
            onCloseEnd: () => {
                // Hide the modal from the UI when closed
                modalElement.style.visibility = "hidden";
            }
        });

        // Set up event listeners
        const submitButton = modalElement.querySelector('#submit-button');
        const cancelButton = modalElement.querySelector('#cancel-button');
        const input = modalElement.querySelector('#passkey-name');
        const errorMessage = modalElement.querySelector('#passkey-name-error');
        // Regex to allow letters, numbers, spaces, underscores and hyphens
        const alphanumericRegex = /^[a-zA-Z0-9 _-]*$/;
        const maxLength = 50; // Maximum character limit
        
        // Get existing passkey names from the DOM - using attribute values directly
        const existingPasskeyNames = Array.from(
            document.querySelectorAll('[data-passkey-name]')
        ).map(element => element.getAttribute('data-passkey-name').trim().toLowerCase());

        const existingPasskeyNameMessage = 'A passkey with this name already exists, please choose a different name';

        // Helper function to update submit button state
        const updateSubmitButtonState = (isValid) => {
            if (isValid) {
                submitButton.classList.remove('disabled');
                submitButton.disabled = false;
            } else {
                submitButton.classList.add('disabled');
                submitButton.disabled = true;
            }
        };

        // Helper function to validate input and update UI accordingly
        const validateInput = () => {
            const inputValue = input.value;
            const trimmedValue = inputValue.trim();
            let isValid = false;
            
            // Check if input is approaching the limit
            if (inputValue.length > maxLength) {
                input.classList.add('invalid');
                errorMessage.style.display = 'block';
                errorMessage.textContent = `Name is too long: ${inputValue.length}/${maxLength} characters`;
            } 
            // Check if the name already exists
            else if (trimmedValue && existingPasskeyNames.includes(trimmedValue.toLowerCase())) {
                input.classList.add('invalid');
                errorMessage.style.display = 'block';
                errorMessage.textContent = existingPasskeyNameMessage;
            }
            else if (trimmedValue && alphanumericRegex.test(trimmedValue)) {
                input.classList.remove('invalid');
                errorMessage.style.display = 'none';
                isValid = true; // Valid input
            } else {
                input.classList.add('invalid');
                errorMessage.style.display = 'block';
                errorMessage.textContent = `Use only letters, numbers, spaces, underscores and hyphens (max ${maxLength} characters)`;
            }
            
            // Update submit button state based on validation
            updateSubmitButtonState(isValid);
            return isValid;
        };

        // Reset the input field and error message when opening the modal
        input.value = '';
        input.classList.remove('invalid');
        errorMessage.style.display = 'none';
        
        // Initially disable the submit button
        updateSubmitButtonState(false);

        // Focus the input when modal opens
        modalInstance.open();
        setTimeout(() => input.focus(), 100); // Small delay to ensure modal is visible

        // Define named handler functions so they can be properly removed later
        const handleInput = () => {
            validateInput();
        };

        const handleSubmit = (e) => {
            e.preventDefault();
            
            // If button is disabled, don't proceed (extra safeguard)
            if (submitButton.classList.contains('disabled')) {
                return;
            }

            // One last validation as a safeguard
            if (!validateInput()) {
                return;
            }

            // Close modal and resolve with the passkey name
            modalInstance.close();
            resolve(DOMPurify.sanitize(input.value.trim()));
        };

        const handleCancel = (e) => {
            e.preventDefault();
            // Clear the input field when cancelling
            input.value = '';
            input.classList.remove('invalid');
            errorMessage.style.display = 'none';
            modalInstance.close();
            M.toast({html: 'Passkey registration cancelled', classes: 'rounded orange'});
            reject(new Error('Passkey registration cancelled'));

        };

        const handleKeyPress = (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                submitButton.click();
            }
        };

        // Add event listeners using the named functions
        input.addEventListener('input', handleInput);
        submitButton.addEventListener('click', handleSubmit);
        cancelButton.addEventListener('click', handleCancel);
        input.addEventListener('keypress', handleKeyPress);

        // Set maxlength attribute but make it slightly higher than our logical limit
        // so our validation can show the error message before browser truncation
        input.setAttribute('maxlength', maxLength + 1);

        // Update the onCloseEnd callback to use the named function references
        modalInstance.options.onCloseEnd = () => {
            // Hide the modal from the UI when closed
            modalElement.style.visibility = "hidden";
            // Clear input field data for privacy/security
            input.value = '';
            input.classList.remove('invalid');
            errorMessage.style.display = 'none';
            // Clean up event listeners to prevent memory leaks
            submitButton.removeEventListener('click', handleSubmit);
            cancelButton.removeEventListener('click', handleCancel);
            input.removeEventListener('input', handleInput);
            input.removeEventListener('keypress', handleKeyPress);
        };
    });
}
