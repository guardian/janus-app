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
 */
export function deletePasskey(passkeyId, csrfToken) {
    try {   
        createAndSubmitForm('/passkey/delete', {
            passkeyId,
            csrfToken
        }); 
    } catch (error) {
        console.error('Error deleting passkey:', error);
        M.toast({ html: 'An error occurred while deleting the passkey', classes: 'rounded red' });
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
                deletePasskey(passkeyId, csrfToken);
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

        // Reset the input field and error message when opening the modal
        input.value = '';
        input.classList.remove('invalid');
        errorMessage.style.display = 'none';

        // Focus the input when modal opens
        modalInstance.open();
        setTimeout(() => input.focus(), 100); // Small delay to ensure modal is visible

        // Define named handler functions so they can be properly removed later
        const handleInput = () => {
            const input_value = input.value;
            
            // Check if input is approaching the limit
            if (input_value.length > maxLength) {
                input.classList.add('invalid');
                errorMessage.style.display = 'block';
                errorMessage.textContent = `Name is too long: ${input_value.length}/${maxLength} characters`;
            } else if (input_value.trim() && alphanumericRegex.test(input_value)) {
                input.classList.remove('invalid');
                errorMessage.style.display = 'none';
            } else {
                input.classList.add('invalid');
                errorMessage.style.display = 'block';
                errorMessage.textContent = `Use only letters, numbers, spaces, underscores and hyphens (max ${maxLength} characters)`;
            }
        };

        const handleSubmit = (e) => {
            e.preventDefault();
            const passkeyName = input.value.trim();

            if (!passkeyName || !alphanumericRegex.test(passkeyName) || passkeyName.length > maxLength) {
                // Show validation error
                input.classList.add('invalid');
                errorMessage.style.display = 'block';
                
                if (passkeyName.length > maxLength) {
                    errorMessage.textContent = `Passkey name too long: maximum ${maxLength} characters allowed`;
                } else {
                    errorMessage.textContent = 'Cannot save passkey name: only letters, numbers, spaces, underscores and hyphens are allowed';
                }
                return;
            }

            // Close modal and resolve with the passkey name
            modalInstance.close();
            resolve(DOMPurify.sanitize(passkeyName));
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
