export async function registerPasskey(csrfToken) {
    const response = await fetch('/passkey/registration-options');
    const publicKeyCredentialCreationOptionsJSON = await response.json();
    const credentialCreationOptions = PublicKeyCredential.parseCreationOptionsFromJSON(publicKeyCredentialCreationOptionsJSON);
    const publicKeyCredential = await navigator.credentials.create({ publicKey: credentialCreationOptions });
    const registrationResponseJSON = publicKeyCredential.toJSON();
    await fetch('/passkey/register', {
        method: 'POST',
        headers: {
            'Content-Type': 'text/plain',
            'Csrf-Token': csrfToken
        },
        body: JSON.stringify(registrationResponseJSON)
    });
}

export function setupRegisterPasskeyButton(buttonSelector) {
    const registerPasskeyButton = document.querySelector(buttonSelector);
    registerPasskeyButton?.addEventListener('click', function (e) {
        e.preventDefault();
        const csrfToken = this.getAttribute('csrf-token');
        registerPasskey(csrfToken).catch(function (err) {
            console.error(err);
        });
    });
}
