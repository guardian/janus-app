document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    const registerPasskeyButton = document.querySelector('#register-passkey');


    async function registerPasskey() {
        const response = await fetch('/passkey/registration-options') 
        const publicKeyCredentialCreationOptionsJSON = await response.json() 
        const credentialCreationOptions = PublicKeyCredential.parseCreationOptionsFromJSON(publicKeyCredentialCreationOptionsJSON); 
        const publicKeyCredential = await navigator.credentials.create({publicKey: credentialCreationOptions}); 
        const registrationResponseJSON = publicKeyCredential.toJSON(); 
        await fetch('/passkey/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain',
                'Csrf-Token': `${registerPasskeyButton.getAttribute('csrf-token')}`
            },
            body: JSON.stringify(registrationResponseJSON)
        });
    }

    registerPasskeyButton?.addEventListener('click', function(e) {
            e.preventDefault();
            registerPasskey().then(function() {
                console.log('Registration successful');
            }).catch(function(err) {
                console.error('Error during registration:', err);
            });
        });

});