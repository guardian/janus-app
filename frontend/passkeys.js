import M from 'materialize-css';

export async function registerPasskey(csrfToken) {
    try {
        const regOptionsResponse = await fetch('/passkey/registration-options');
        const regOptionsResponseJson = await regOptionsResponse.json();
        const credentialCreationOptions = PublicKeyCredential.parseCreationOptionsFromJSON(regOptionsResponseJson);
        const publicKeyCredential = await navigator.credentials.create({publicKey: credentialCreationOptions});
        const passkeyName = await getPasskeyNameFromUser();

        createAndSubmitForm('/passkey/register', {
            passkey: JSON.stringify(publicKeyCredential.toJSON()),
            csrfToken: csrfToken,
            passkeyName: passkeyName
        });
    } catch (err) {
        console.error(err);
        M.toast({html: err.message, classes: 'rounded red'});
    }
}

export function setUpRegisterPasskeyButton(buttonSelector) {
    const registerPasskeyButton = document.querySelector(buttonSelector);
    registerPasskeyButton?.addEventListener('click', function (e) {
        e.preventDefault();
        const csrfToken = this.getAttribute('csrf-token');
        registerPasskey(csrfToken).catch(function (err) {
            console.error(err);
        });
    });
}

export async function authenticatePasskey(targetHref, csrfToken)  {
    try {
        const authOptionsResponse = await fetch('/passkey/auth-options');
        const authOptionsResponseJson = await authOptionsResponse.json();
        const credentialGetOptions = PublicKeyCredential.parseRequestOptionsFromJSON(authOptionsResponseJson);
        const publicKeyCredential = await navigator.credentials.get({publicKey: credentialGetOptions});

        createAndSubmitForm(targetHref, {
            credentials: JSON.stringify(publicKeyCredential.toJSON()),
            csrfToken: csrfToken
        });
    } catch (err) {
        console.error(err);
        M.toast({html: err.message, classes: 'rounded red'});
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
        input.setAttribute('value', value);
        form.appendChild(input);
    });

    document.body.append(form);
    form.submit();
}

export function setUpProtectedLinks(links) {
    links.forEach((link) => {
        link.addEventListener('click', function (e) {
            e.preventDefault();
            console.log('clicked');
            const csrfToken = link.getAttribute('csrf-token');
            const targetHref = link.href;
            authenticatePasskey(targetHref, csrfToken).catch(function (err) {
                console.error(err);
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
        // Create Materialize modal structure
        const modalId = 'passkey-name-modal';
        const modalHtml = `
        <div id="${modalId}" class="modal">
            <div class="modal-content">
                <h4 class="orange-text">Passkey Name</h4>
                <p>Give this passkey a name to help you recognize it later.</p>
                <div class="input-field">
                    <input type="text" id="passkey-name" class="validate" placeholder="e.g. Macbook, Phone" required>
                    <div id="passkey-name-error" class="error-message red-text" style="display: none;">Please enter a name for your passkey</div>
                </div>
            </div>
            <div class="modal-footer">
                <a href="#!" id="cancel-button" class="modal-close waves-effect waves-light btn-flat">Cancel</a>
                <a href="#!" id="submit-button" class="waves-effect waves-light btn orange">Save</a>
            </div>
        </div>`;
        
        document.body.insertAdjacentHTML('beforeend', modalHtml);
        
        const modalElement = document.getElementById(modalId);
        
        // Initialize Materialize modal
        const modalInstance = M.Modal.init(modalElement, {
            dismissible: false, // User must use buttons to close
            onCloseEnd: () => {
                // Clean up the modal from the DOM when closed
                modalElement.remove();
            }
        });
        
        // Set up event listeners
        const submitButton = modalElement.querySelector('#submit-button');
        const cancelButton = modalElement.querySelector('#cancel-button');
        const input = modalElement.querySelector('#passkey-name');
        const errorMessage = modalElement.querySelector('#passkey-name-error');
        
        // Focus the input when modal opens
        modalInstance.open();
        setTimeout(() => input.focus(), 100); // Small delay to ensure modal is visible
        
        // Clear error when typing
        input.addEventListener('input', () => {
            if (input.value.trim()) {
                input.classList.remove('invalid');
                errorMessage.style.display = 'none';
            }
        });
        
        // Handle form submission
        submitButton.addEventListener('click', (e) => {
            e.preventDefault();
            const passkeyName = input.value.trim();
            
            if (!passkeyName) {
                // Show validation error
                input.classList.add('invalid');
                errorMessage.style.display = 'block';
                return;
            }
            
            // Close modal and resolve with the passkey name
            modalInstance.close();
            resolve(passkeyName);
        });
        
        // Handle cancel button
        cancelButton.addEventListener('click', (e) => {
            e.preventDefault();
            modalInstance.close();
            reject(new Error('Passkey registration cancelled'));
        });
        
        // Handle Enter key for form submission
        input.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                submitButton.click();
            }
        });
    });
}
