import DOMPurify from 'dompurify';
import M from 'materialize-css';

/**
 * Prompts the user to name a passkey via a modal dialog
 * @returns {Promise<string>} A promise that resolves with the passkey name
 */
export function getPasskeyNameFromUser() {
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
        
        // Get existing passkey names from the DOM - using attribute values directly
        const existingPasskeyNames = Array.from(
            document.querySelectorAll('[data-passkey-name]')
        ).map(element => element.getAttribute('data-passkey-name').trim().toLowerCase());

        const existingPasskeyNameMessage = 'A passkey with this name already exists, please choose a different name';

        // Helper function to update submit button state
        const updateSubmitButtonState = (isValid) => {
            if (isValid) {
                submitButton.classList.remove('disabled');
                submitButton.disabled = false;
            } else {
                submitButton.classList.add('disabled');
                submitButton.disabled = true;
            }
        };

        // Helper function to validate input and update UI accordingly
        const validateInput = () => {
            const inputValue = input.value;
            const trimmedValue = inputValue.trim();
            let isValid = false;
            
            // Check if input is approaching the limit
            if (inputValue.length > maxLength) {
                input.classList.add('invalid');
                errorMessage.style.display = 'block';
                errorMessage.textContent = `Name is too long: ${inputValue.length}/${maxLength} characters`;
            } 
            // Check if the name already exists
            else if (trimmedValue && existingPasskeyNames.includes(trimmedValue.toLowerCase())) {
                input.classList.add('invalid');
                errorMessage.style.display = 'block';
                errorMessage.textContent = existingPasskeyNameMessage;
            }
            else if (trimmedValue && alphanumericRegex.test(trimmedValue)) {
                input.classList.remove('invalid');
                errorMessage.style.display = 'none';
                isValid = true; // Valid input
            } else {
                input.classList.add('invalid');
                errorMessage.style.display = 'block';
                errorMessage.textContent = `Use only letters, numbers, spaces, underscores and hyphens (max ${maxLength} characters)`;
            }
            
            // Update submit button state based on validation
            updateSubmitButtonState(isValid);
            return isValid;
        };

        // Reset the input field and error message when opening the modal
        input.value = '';
        input.classList.remove('invalid');
        errorMessage.style.display = 'none';
        
        // Initially disable the submit button
        updateSubmitButtonState(false);

        // Focus the input when modal opens
        modalInstance.open();
        setTimeout(() => input.focus(), 100); // Small delay to ensure modal is visible

        // Define named handler functions so they can be properly removed later
        const handleInput = () => {
            validateInput();
        };

        const handleSubmit = (e) => {
            e.preventDefault();
            
            // If button is disabled, don't proceed (extra safeguard)
            if (submitButton.classList.contains('disabled')) {
                return;
            }

            // One last validation as a safeguard
            if (!validateInput()) {
                return;
            }

            // Close modal and resolve with the passkey name
            modalInstance.close();
            resolve(DOMPurify.sanitize(input.value.trim()));
        };

        const handleCancel = (e) => {
            e.preventDefault();
            // Clear the input field when cancelling
            input.value = '';
            input.classList.remove('invalid');
            errorMessage.style.display = 'none';
            modalInstance.close();
            M.toast({html: DOMPurify.sanitize('Passkey registration cancelled'), classes: 'rounded orange'});
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
            // Clear input field data for privacy/security
            input.value = '';
            input.classList.remove('invalid');
            errorMessage.style.display = 'none';
            // Clean up event listeners to prevent memory leaks
            submitButton.removeEventListener('click', handleSubmit);
            cancelButton.removeEventListener('click', handleCancel);
            input.removeEventListener('input', handleInput);
            input.removeEventListener('keypress', handleKeyPress);
        };
    });
}
