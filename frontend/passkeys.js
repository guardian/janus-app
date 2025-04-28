import DOMPurify from 'dompurify';

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
    const authOptionsResponse = await fetch("/passkey/auth-options");
    const publicKeyCredentialRequestOptionsJSON = await authOptionsResponse.json();
    const credentialGetOptions = PublicKeyCredential.parseRequestOptionsFromJSON(publicKeyCredentialRequestOptionsJSON);
    const publicKeyCredential = await navigator.credentials.get({ publicKey: credentialGetOptions});
    console.log("publicKeyCredential: ", publicKeyCredential);

    const response = await fetch(targetHref, {
        method: 'POST',
        headers: {
            // 'Content-Type': 'application/json',
            'Content-Type': 'text/plain',
            'Csrf-Token': csrfToken
        },
        body: JSON.stringify(publicKeyCredential.toJSON())
    });

    if (response.ok) {
        // Replace content of page with content of response
        document.body.innerHTML = DOMPurify.sanitize(await response.text());
        // Update current browser URL with target URL
        history.pushState({}, '', response.url);
    }
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
