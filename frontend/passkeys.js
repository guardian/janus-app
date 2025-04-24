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

export async function authenticatePasskey(targetHref, csrfToken)  {
    console.log("starting authentication");
    const response = await fetch("/passkey/auth-options");
    const publicKeyCredentialRequestOptionsJSON = await response.json();
    const credentialGetOptions = PublicKeyCredential.parseRequestOptionsFromJSON(publicKeyCredentialRequestOptionsJSON);
    const publicKeyCredential = await navigator.credentials.get({ publicKey: credentialGetOptions});
    console.log("publicKeyCredential: ", publicKeyCredential);

    const authResponseJSON = publicKeyCredential.toJSON();
    await fetch(targetHref, {
        method: 'POST',
        headers: {
            // 'Content-Type': 'application/json',
            'Content-Type': 'text/plain',
            'Csrf-Token': csrfToken
        },
        body: JSON.stringify(authResponseJSON)
    });
}

export function setupAuthButtons(buttonSelector) {
    const requestAuthButtons = document.querySelector(buttonSelector);
    console.log(requestAuthButtons.length);
    requestAuthButtons.addEventListener('click', function (e) {
        e.preventDefault();
        console.log('clicked');

        const csrfToken = this.getAttribute('csrf-token');
        const targetHref = this.href;
        authenticatePasskey(targetHref, csrfToken).catch(function (err) {
            console.error(err);
        });
    });

    // requestAuthButtons.forEach((button) => {
    //     // console.log('button',button);
    //     addEventListener('click', function (e) {
    //         e.preventDefault();
    //         console.log('clicked');
    //
    //         const csrfToken = button.getAttribute('csrf-token');
    //         const targetHref = button.href;
    //         authenticatePasskey(targetHref, csrfToken).catch(function (err) {
    //             console.error(err);
    //             });
    //     });
    // });
}