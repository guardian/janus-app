export async function registerPasskey(csrfToken) {
    const regOptionsResponse = await fetch('/passkey/registration-options');
    const regOptionsResponseJson = await regOptionsResponse.json();
    const credentialCreationOptions = PublicKeyCredential.parseCreationOptionsFromJSON(regOptionsResponseJson);
    const publicKeyCredential = await navigator.credentials.create({ publicKey: credentialCreationOptions });
    const passkeyName = await getPasskeyNameFromUser();

    createAndSubmitForm('/passkey/register', {
        passkey: JSON.stringify(publicKeyCredential.toJSON()),
        csrfToken: csrfToken,
        passkeyName: passkeyName
    });
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
    const authOptionsResponse = await fetch("/passkey/auth-options");
    const authOptionsResponseJson = await authOptionsResponse.json();
    const credentialGetOptions = PublicKeyCredential.parseRequestOptionsFromJSON(authOptionsResponseJson);
    const publicKeyCredential = await navigator.credentials.get({ publicKey: credentialGetOptions});

    createAndSubmitForm(targetHref, {
        credentials: JSON.stringify(publicKeyCredential.toJSON()),
        csrfToken: csrfToken
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
        input.setAttribute('value', value);
        form.appendChild(input);
    });

    document.getElementsByTagName('body')[0].appendChild(form);
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
                <h4 class="orange-text">Name Your Passkey</h4>
                <p>Give this passkey a name to help you recognize it later.</p>
                <div class="input-field">
                    <input type="text" id="passkey-name" class="validate" placeholder="e.g. Macbook, Phone" required>
                    <label for="passkey-name">Passkey Name</label>
                </div>
            </div>
            <div class="modal-footer">
                <a href="#!" id="cancel-button" class="modal-close waves-effect waves-light btn-flat">Cancel</a>
                <a href="#!" id="submit-button" class="waves-effect waves-light btn orange">Save</a>
            </div>
        </div>`;
        
        // Add modal to document
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
        
        // Focus the input when modal opens
        modalInstance.open();
        setTimeout(() => input.focus(), 100); // Small delay to ensure modal is visible
        
        // Handle form submission
        submitButton.addEventListener('click', (e) => {
            e.preventDefault();
            const passkeyName = input.value.trim();
            
            if (!passkeyName) {
                // Show validation error
                input.classList.add('invalid');
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
            reject(new Error('User cancelled passkey naming'));
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
