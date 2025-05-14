import DOMPurify from 'dompurify';
import M from 'materialize-css';

export async function registerPasskey(csrfToken) {
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
        const passkeyName = await getPasskeyNameFromUser();

        createAndSubmitForm('/passkey/register', {
            passkey: JSON.stringify(publicKeyCredential.toJSON()),
            csrfToken: csrfToken,
            passkeyName: passkeyName
        });
    } catch (err) {
        console.error('Error during passkey registration:', err);
        M.toast({ html: 'Failed to register passkey. Please try again.', classes: 'rounded red' });
    }
}

export function setUpRegisterPasskeyButton(buttonSelector) {
    const registerPasskeyButton = document.querySelector(buttonSelector);
    registerPasskeyButton?.addEventListener('click', function (e) {
        e.preventDefault();
        const csrfToken = this.getAttribute('csrf-token');
        registerPasskey(csrfToken).catch(function (err) {
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
        M.toast({ html: 'Authentication failed. Please try again.', classes: 'rounded red' });
    }
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
            modalInstance.close();
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
            // Clean up event listeners to prevent memory leaks
            submitButton.removeEventListener('click', handleSubmit);
            cancelButton.removeEventListener('click', handleCancel);
            input.removeEventListener('input', handleInput);
            input.removeEventListener('keypress', handleKeyPress);
        };
    });
}
