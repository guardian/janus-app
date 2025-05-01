export async function registerPasskey(csrfToken) {
    const response = await fetch('/passkey/registration-options');
    const publicKeyCredentialCreationOptionsJSON = await response.json();
    const credentialCreationOptions = PublicKeyCredential.parseCreationOptionsFromJSON(publicKeyCredentialCreationOptionsJSON);
    const publicKeyCredential = await navigator.credentials.create({ publicKey: credentialCreationOptions });
    const registrationResponseJSON = publicKeyCredential.toJSON();
    await fetch('/passkey/register', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Csrf-Token': csrfToken
        },
        body: {
            "passkeyName": "TODO",
            "passkey": JSON.stringify(registrationResponseJSON)}
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
    console.log("starting authentication");
    const authOptionsResponse = await fetch("/passkey/auth-options");
    const publicKeyCredentialRequestOptionsJSON = await authOptionsResponse.json();
    const credentialGetOptions = PublicKeyCredential.parseRequestOptionsFromJSON(publicKeyCredentialRequestOptionsJSON);
    const publicKeyCredential = await navigator.credentials.get({ publicKey: credentialGetOptions});
    console.log("publicKeyCredential: ", publicKeyCredential);

    // Add a form to the DOM so that it can be submitted at page level
    const form = document.createElement('form');
    form.setAttribute('method', 'post');
    form.setAttribute('action', targetHref);
    const credsInput = document.createElement('input');
    credsInput.setAttribute('type','hidden');
    credsInput.setAttribute('name','credentials');
    credsInput.setAttribute('value', JSON.stringify(publicKeyCredential.toJSON()));
    const csrfTokenInput = document.createElement('input');
    csrfTokenInput.setAttribute('type','hidden');
    csrfTokenInput.setAttribute('name','csrfToken');
    csrfTokenInput.setAttribute('value', csrfToken);
    form.appendChild(credsInput);
    form.appendChild(csrfTokenInput);
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
