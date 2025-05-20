import DOMPurify from 'dompurify';
import M from 'materialize-css';

// Custom error class for user cancellation
class UserCancellationError extends Error {
    constructor(message = 'Operation cancelled by user') {
        super(message);
        this.name = 'UserCancellationError';
    }
}

export async function registerPasskey(csrfToken, existingNames = []) {
    try {
        const regOptionsResponse = await fetch('/passkey/registration-options', {
            method: 'GET',
            headers: {
                'X-CSRF-Token': csrfToken // Securely include CSRF token in headers
            }
        });
        const regOptionsResponseJson = await regOptionsResponse.json();
        const credentialCreationOptions = PublicKeyCredential.parseCreationOptionsFromJSON(regOptionsResponseJson);
        const publicKeyCredential = await navigator.credentials.create({ publicKey: credentialCreationOptions });
        // Pass existing names to the modal for duplicate checking
        const passkeyName = await getPasskeyNameFromUser(existingNames);

        // Show success toast before submitting form
        M.toast({ 
            html: DOMPurify.sanitize(`Passkey "${passkeyName}" registered successfully`), 
            classes: 'rounded green'
        });
        
        // Delay form submission to allow toast to be visible
        setTimeout(() => {
            createAndSubmitForm('/passkey/register', {
                passkey: JSON.stringify(publicKeyCredential.toJSON()),
                csrfToken: csrfToken,
                passkeyName: passkeyName
            });
        }, 2000);
    } catch (err) {
        // Check for cancellation using error type instead of message string
        if (err instanceof UserCancellationError) {
            // User cancellation is already handled in getPasskeyNameFromUser()
            return;
        }
        
        console.error('Error during passkey registration:', err);
        M.toast({ html: DOMPurify.sanitize('Failed to register passkey. Please try again.'), classes: 'rounded red' });
    }
}

export function setUpRegisterPasskeyButton(selector) {
    const registerButton = document.querySelector(selector);
    if (!registerButton) { return };

    registerButton?.addEventListener('click', function (e) {
        e.preventDefault();
        const csrfToken = this.getAttribute('csrf-token');
        // Get existing passkey names from data attribute
        const existingNamesAttribute = this.getAttribute('data-existing-names') || '';
        const existingNames = existingNamesAttribute ? existingNamesAttribute.split('|') : [];
        
        // Pass existing names to registerPasskey
        registerPasskey(csrfToken, existingNames).catch(function (err) {
            console.error('Error setting up register passkey button:', err);
        });
    });
}

export async function authenticatePasskey(targetHref, csrfToken) {
    try {
        const authOptionsResponse = await fetch('/passkey/auth-options', {
            method: 'GET',
            headers: {
                'X-CSRF-Token': csrfToken // Securely include CSRF token in headers
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
        M.toast({ html: DOMPurify.sanitize('Authentication failed. Please try again.'), classes: 'rounded red' });
    }
}

export function deletePasskey(passkeyId, passkeyName, csrfToken) {
    try {
        // Validate inputs before making the request
        if (!passkeyId || typeof passkeyId !== 'string' || !csrfToken || typeof csrfToken !== 'string') {
            throw new Error('Invalid parameters for passkey deletion');
        }

        // Use fetch API instead of form submission to prevent immediate page refresh
        fetch('/passkey/delete', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-CSRF-Token': csrfToken,
                'Cache-Control': 'no-cache, no-store, must-revalidate',
                'Pragma': 'no-cache'
            },
            credentials: 'same-origin', // Include cookies in the request
            // Format data as form data instead of JSON to match server expectations
            body: `passkeyId=${encodeURIComponent(passkeyId)}&csrfToken=${encodeURIComponent(csrfToken)}`
        }).then(response => {
            if (!response.ok) {
                return response.text().then(text => {
                    throw new Error(`Server error: ${response.status} ${text || 'Unknown error'}`);
                });
            }
            return response.json().catch(() => {
                // If JSON parsing fails, the response might be empty or plain text
                return { success: true };
            });
        }).then(data => {
            if (data && data.success === false) {
                throw new Error(data.message || 'Unknown error occurred');
            }            
            M.toast({ 
                html: DOMPurify.sanitize(`Passkey "${passkeyName}" deleted successfully`), 
                classes: 'rounded green',
            });
            
            // Refresh the page after the toast has been shown
            setTimeout(() => {
                window.location.reload();
            }, 2000);
        }).catch(error => {
            console.error('Error deleting passkey:', error);
            M.toast({ 
                html: DOMPurify.sanitize('An error occurred while deleting the passkey'), 
                classes: 'rounded red' 
            });
        });
    } catch (error) {
        console.error('Error setting up passkey deletion:', error);
        M.toast({ 
            html: DOMPurify.sanitize('An error occurred while deleting the passkey'), 
            classes: 'rounded red' 
        });
    }
}

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
                M.toast({ html: DOMPurify.sanitize('Error: Passkey ID not found'), classes: 'rounded red' });
                return;
            }
            
            if (!csrfToken) {
                console.error('No CSRF token found');
                M.toast({ html: DOMPurify.sanitize('Error: Security token not found'), classes: 'rounded red' });
                return;
            }
            
            if (confirm(`Are you sure you want to delete the passkey "${passkeyName}"?`)) {
                deletePasskey(passkeyId, passkeyName, csrfToken);
            } else {
                M.toast({ html: DOMPurify.sanitize('Passkey deletion cancelled'), classes: 'rounded blue' });
            }
        });
    });
}

function createAndSubmitForm(targetHref, formData) {
    const form = document.createElement('form');
    form.setAttribute('method', 'post');
    form.setAttribute('action', targetHref);

    Object.entries(formData).forEach(([name, value]) => {
        const input = document.createElement('input');
        input.setAttribute('type', 'hidden');
        input.setAttribute('name', name);
        input.setAttribute('value', DOMPurify.sanitize(value));
        form.appendChild(input);
    });

    document.body.append(form);
    form.submit();
}

export function setUpProtectedLinks(links) {
    if (!links.length) {
        return;
    }

    links.forEach((link) => {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            const csrfToken = link.getAttribute('csrf-token');
            const targetHref = link.href;
            authenticatePasskey(targetHref, csrfToken).catch(function (err) {
                console.error('Error setting up protected link:', err);
            });
        });
    });
}

/**
 * Prompts the user to name a passkey via a modal dialog
 * @param {string[]} existingNames - List of existing passkey names to check for duplicates
 * @returns {Promise<string>} A promise that resolves with the passkey name
 */
function getPasskeyNameFromUser(existingNames = []) {
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
        // Use constants from window
        const alphanumericRegex = new RegExp(window.ValidationConstants.PASSKEY_NAME.REGEX_PATTERN);
        const maxLength = window.ValidationConstants.PASSKEY_NAME.MAX_LENGTH;

        // Reset the input field and error message when opening the modal
        input.value = '';
        input.classList.remove('invalid');
        errorMessage.style.display = 'none';

        // Focus the input when modal opens
        modalInstance.open();
        setTimeout(() => input.focus(), 100); // Small delay to ensure modal is visible

        /**
         * Validates the passkey name format
         * @param {string} name - The passkey name to validate
         * @returns {object} - Validation result with isValid and message properties
         */
        const validateNameFormat = (name) => {
            const trimmedName = name.trim();
            
            if (!trimmedName) {
                return { isValid: false, message: 'Passkey name cannot be empty' };
            }
            
            if (trimmedName.length > maxLength) {
                return { 
                    isValid: false, 
                    message: `Passkey name too long: maximum ${maxLength} characters allowed`
                };
            }
            
            if (!alphanumericRegex.test(trimmedName)) {
                return { 
                    isValid: false, 
                    message: 'Cannot save passkey name: only letters, numbers, spaces, underscores and hyphens are allowed'
                };
            }
            
            return { isValid: true };
        };

        /**
         * Checks if the name is a duplicate
         * @param {string} name - The passkey name to check
         * @returns {object} - Validation result with isDuplicate and message properties
         */
        const checkForDuplicate = (name) => {
            const trimmedName = name.trim();
            
            if (existingNames.some(existingName => existingName.toLowerCase() === trimmedName.toLowerCase())) {
                return { 
                    isDuplicate: true, 
                    message: `A passkey with the name "${trimmedName}" already exists` 
                };
            }
            
            return { isDuplicate: false };
        };

        /**
         * Updates validation UI elements
         * @param {boolean} isValid - Whether the input is valid
         * @param {string} message - Error message to display if invalid
         */
        const updateValidationUI = (isValid, message = '') => {
            if (isValid) {
                input.classList.remove('invalid');
                errorMessage.style.display = 'none';
            } else {
                input.classList.add('invalid');
                errorMessage.style.display = 'block';
                errorMessage.textContent = message;
            }
        };

        // Define named handler functions so they can be properly removed later
        const handleInput = () => {
            const inputValue = input.value;
            
            // First check format
            const formatValidation = validateNameFormat(inputValue);
            if (!formatValidation.isValid) {
                updateValidationUI(false, formatValidation.message);
                return;
            }
            
            // Then check for duplicates
            const duplicationCheck = checkForDuplicate(inputValue);
            if (duplicationCheck.isDuplicate) {
                updateValidationUI(false, duplicationCheck.message);
                return;
            }
            
            // All checks passed
            updateValidationUI(true);
        };

        const handleSubmit = (e) => {
            e.preventDefault();
            const passkeyName = input.value.trim();
            
            // Perform validation
            const formatValidation = validateNameFormat(passkeyName);
            if (!formatValidation.isValid) {
                updateValidationUI(false, formatValidation.message);
                return;
            }
            
            // Check for duplicate names
            const duplicationCheck = checkForDuplicate(passkeyName);
            if (duplicationCheck.isDuplicate) {
                updateValidationUI(false, duplicationCheck.message);
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
            M.toast({ 
                html: DOMPurify.sanitize('Passkey registration cancelled'), 
                classes: 'rounded blue',
            });
            // Use custom error class instead of generic Error
            reject(new UserCancellationError('Passkey registration cancelled'));
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