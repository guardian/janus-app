import DOMPurify from 'dompurify';
import M from 'materialize-css';

// Regex to allow letters, numbers, spaces, underscores and hyphens
const VALID_REGEX = /^[a-zA-Z0-9 _-]*$/;
const MAX_LENGTH = 50; // Maximum character limit
const FOCUS_DELAY = 100;
const VALIDATION_MESSAGES = {
    TOO_LONG: (length) => `Name is too long: ${length}/${MAX_LENGTH} characters`,
    INVALID_CHARS: `Use only letters, numbers, spaces, underscores and hyphens (max ${MAX_LENGTH} characters)`,
    EXISTING_NAME: 'A passkey with this name already exists, please choose a different name'
};

const validateRequiredDOMElements = (elements) => {
    for (const [name, element] of Object.entries(elements)) {
        if (!element) {
            throw new Error(`Required element not found: ${name}`);
        }
    }
};

/**
 * Initializes and opens the passkey name modal
 * @param {HTMLElement} modalElement - The modal element
 * @param {HTMLInputElement} input - The name input field
 * @param {HTMLButtonElement} submitButton - The submit button
 * @param {HTMLElement} userAccountContainer - Container to make inert (optional)
 * @param {HTMLElement} errorMessage - Error message element
 * @returns {Object} Materialize modal instance
 */
const initializeModal = (modalElement, input, submitButton, userAccountContainer, errorMessage) => {
    modalElement.style.display = 'none';
    
    // Initialize Materialize modal
    const modalInstance = M.Modal.init(modalElement, {
        dismissible: false, // User must use buttons to close
    });
    
    // Reset the input field and error message when opening the modal
    input.value = '';
    input.classList.remove('invalid');
    errorMessage.style.display = 'none';
    
    // Initially disable the submit button
    updateSubmitButtonState(submitButton, false);
    
    // Focus the input when modal opens
    modalInstance.open();
    modalElement.inert = false;
    if (userAccountContainer) {
        userAccountContainer.inert = true;
    }
    setTimeout(() => input.focus(), FOCUS_DELAY);
    
    return modalInstance;
};

// Helper function to update submit button state
const updateSubmitButtonState = (submitButton, isValid) => {
    if (isValid) {
        submitButton.classList.remove('disabled');
        submitButton.disabled = false;
    } else {
        submitButton.classList.add('disabled');
        submitButton.disabled = true;
    }
};

const setInvalidState = (input, errorMessage, message) => {
    input.classList.add('invalid');
    errorMessage.style.display = 'block';
    errorMessage.textContent = message;
};

const setValidState = (input, errorMessage) => {
    input.classList.remove('invalid');
    errorMessage.style.display = 'none';
};

/**
 * Validates user input for passkey name and updates UI state accordingly
 * @param {HTMLInputElement} input - The input element to validate
 * @param {string[]} existingPasskeyNames - Array of existing passkey names (lowercase)
 * @param {HTMLButtonElement} submitButton - Submit button to enable/disable
 * @param {HTMLElement} errorMessage - Error message element to show/hide
 * @returns {boolean} True if input is valid, false otherwise
 */
const validateInput = (input, existingPasskeyNames, submitButton, errorMessage) => {
    const inputValue = DOMPurify.sanitize(input.value);
    const trimmedValue = inputValue.trim();
    let isValid = false;
    
    if (inputValue.length > MAX_LENGTH) {
        setInvalidState(input, errorMessage, VALIDATION_MESSAGES.TOO_LONG(inputValue.length));
    } 
    else if (trimmedValue && existingPasskeyNames.includes(trimmedValue.toLowerCase())) {
        setInvalidState(input, errorMessage, VALIDATION_MESSAGES.EXISTING_NAME);
    }
    else if (trimmedValue && VALID_REGEX.test(trimmedValue)) {
        setValidState(input, errorMessage);
        isValid = true;
    } else {
        setInvalidState(input, errorMessage, VALIDATION_MESSAGES.INVALID_CHARS);
    }
    
    updateSubmitButtonState(submitButton, isValid);
    return isValid;
};

/**
 * Cleans up modal state and removes event listeners
 * @param {HTMLElement} modalElement - The modal element
 * @param {HTMLInputElement} input - The name input field
 * @param {HTMLElement} userAccountContainer - Container to restore from inert
 * @param {Object} handlers - Object containing event handler functions
 * @param {HTMLElement} errorMessage - Error message element
 */
const cleanupModal = (modalElement, input, userAccountContainer, handlers, errorMessage) => {
    const submitButton = modalElement.querySelector('#submit-button');
    const cancelButton = modalElement.querySelector('#cancel-button');
    
    modalElement.style.display = 'none';
    modalElement.inert = true;
    if (userAccountContainer) {
        userAccountContainer.inert = false;
    }
    input.value = '';
    input.classList.remove('invalid');
    errorMessage.style.display = 'none';
    
    submitButton.removeEventListener('click', handlers.handleSubmit);
    cancelButton.removeEventListener('click', handlers.handleCancel);
    input.removeEventListener('input', handlers.handleInput);
    input.removeEventListener('keypress', handlers.handleKeyPress);
};

/**
 * Prompts the user to name a passkey via a modal dialog
 * @returns {Promise<string>} A promise that resolves with the passkey name
 */
export function getPasskeyNameFromUser() {
    return new Promise((resolve, reject) => {
        const modalElement = document.getElementById('passkey-name-modal');
        const submitButton = modalElement?.querySelector('#submit-button');
        const cancelButton = modalElement?.querySelector('#cancel-button');
        const input = modalElement?.querySelector('#passkey-name');
        const errorMessage = modalElement?.querySelector('#passkey-name-error');
        const userAccountContainer = document.querySelector('#user-account-container');

        // Get existing passkey names from the DOM
        const existingPasskeyNames = Array.from(
            document.querySelectorAll('[data-passkey-name]')
        ).map(element => element.getAttribute('data-passkey-name').trim().toLowerCase());

        validateRequiredDOMElements({ modalElement, submitButton, cancelButton, input, errorMessage });
        
        const modalInstance = initializeModal(modalElement, input, submitButton, userAccountContainer, errorMessage);

        // Define handlers inside promise scope
        const handleInput = () => {
            validateInput(input, existingPasskeyNames, submitButton, errorMessage);
        };
        const handleSubmit = (e) => {
            e.preventDefault();
            
            // If button is disabled, don't proceed (extra safeguard)
            if (submitButton.classList.contains('disabled')) {
                return;
            }

            // One last validation as a safeguard
            if (!validateInput(input, existingPasskeyNames, submitButton, errorMessage)) {
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

        const handlers = {
            handleInput,
            handleSubmit,
            handleCancel,
            handleKeyPress
        };

        input.addEventListener('input', handleInput);
        submitButton.addEventListener('click', handleSubmit);
        cancelButton.addEventListener('click', handleCancel);
        input.addEventListener('keypress', handleKeyPress);

        // Set maxlength attribute but make it slightly higher than our logical limit
        // so our validation can show the error message before browser truncation
        input.setAttribute('maxlength', MAX_LENGTH + 1);

        modalInstance.options.onCloseEnd = () => {
            cleanupModal(modalElement, input, userAccountContainer, handlers, errorMessage);
        };
    });
}