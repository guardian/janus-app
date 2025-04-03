document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    async function registerPasskey() {
        const response = await fetch('/passkey/registration-options') //fetch PublicKeyCredentialCreationOptions as JSON string
        const publicKeyCredentialCreationOptionsJSON = await response.json() // convert to JSONObject
        const credentialCreationOptions = PublicKeyCredential.parseCreationOptionsFromJSON(publicKeyCredentialCreationOptionsJSON); // convert to PublicKeyCredentialCreationOptions
        const publicKeyCredential = await navigator.credentials.create({publicKey: credentialCreationOptions}); // create PublicKeyCredential
        console.log('publicKeyCredential: ', publicKeyCredential);
        const registrationResponseJSON = publicKeyCredential.toJSON(); // convert to JSONObject
        await fetch('/passkey/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain',
                'Csrf-Token': '@{CSRF.getToken.get.value}'
            },
            body: JSON.stringify(registrationResponseJSON)
        });
    }

    document.querySelectorAll('#register-passkey').forEach(function(btn) {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            registerPasskey().then(function() {
                console.log('Registration successful');
            }).catch(function(err) {
                console.error('Error during registration:', err);
            });
        });
    })

});