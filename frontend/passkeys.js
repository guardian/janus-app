export async function registerPasskey(csrfToken) {
    const regOptionsResponse = await fetch('/passkey/registration-options');
    const regOptionsResponseJson = await regOptionsResponse.json();
    const credentialCreationOptions = PublicKeyCredential.parseCreationOptionsFromJSON(regOptionsResponseJson);
    const publicKeyCredential = await navigator.credentials.create({ publicKey: credentialCreationOptions });

    createAndSubmitForm('/passkey/register', {
        passkey: JSON.stringify(publicKeyCredential.toJSON()),
        csrfToken: csrfToken,
        passkeyName: 'hello again passkeyName'
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
