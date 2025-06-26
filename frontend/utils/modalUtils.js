/**
 * Modal utility functions for user interactions
 * 
 * This module provides reusable modal dialogs for:
 * - Collecting passkey names from users with validation
 * - Showing confirmation dialogs with custom messages
 * - Managing modal lifecycle (creation, cleanup, event handling)
 * 
 * Features:
 * - Input validation with real-time feedback
 * - Accessibility support (inert attribute management)
 * - XSS protection via DOMPurify sanitization
 * - Proper event listener cleanup to prevent memory leaks
 * - Debounced validation for better performance
 * - Keyboard navigation support (Enter key handling)
 * 
 * Dependencies:
 * - DOMPurify: Content sanitization
 * - Materialize CSS: Modal component styling and behavior
 * - toastMessages: User feedback notifications
 * 
 * @fileoverview Modal utilities for passkey management and user confirmations
 */

import DOMPurify from 'dompurify';
import M from 'materialize-css';
import { displayToast, messageType } from './toastMessages';

// Regex to allow letters, numbers, spaces, underscores and hyphens
const VALID_REGEX = /^[a-zA-Z0-9 _-]*$/;
const MAX_LENGTH = 50; // Maximum character limit
const FOCUS_DELAY = 100;
const DEBOUNCE_TIME = 150;
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

/**
 * Validation function that returns a single validation error
 * @param {string} inputValue - The value to validate
 * @param {string[]} existingPasskeyNames - Array of existing passkey names (lowercase)
 * @returns {string|null} Error message or null if valid
 */
const validatePasskeyName = (inputValue, existingPasskeyNames) => {
    const sanitizedValue = DOMPurify.sanitize(inputValue);
    const trimmedValue = sanitizedValue.trim();
    
    // Check if input is empty first
    if (!trimmedValue) {
        return VALIDATION_MESSAGES.INVALID_CHARS;
    }
    
    // Check length
    if (sanitizedValue.length > MAX_LENGTH) {
        return VALIDATION_MESSAGES.TOO_LONG(sanitizedValue.length);
    }
    
    // Check for invalid characters
    if (!VALID_REGEX.test(trimmedValue)) {
        return VALIDATION_MESSAGES.INVALID_CHARS;
    }
    
    // Check for duplicate names
    if (existingPasskeyNames.includes(trimmedValue.toLowerCase())) {
        return VALIDATION_MESSAGES.EXISTING_NAME;
    }
    
    return null; // Valid
};

/**
 * Updates UI state based on validation error
 * @param {string|null} error - Error message or null if valid
 * @param {HTMLInputElement} input - The input element
 * @param {HTMLButtonElement} submitButton - Submit button to enable/disable
 * @param {HTMLElement} errorMessage - Error message element
 */
const updateValidationUI = (error, input, submitButton, errorMessage) => {
    const isValid = error === null;
    
    if (isValid) {
        input.classList.remove('invalid');
        errorMessage.style.display = 'none';
        errorMessage.textContent = '';
    } else {
        input.classList.add('invalid');
        errorMessage.style.display = 'block';
        errorMessage.textContent = error;
    }
    
    updateSubmitButtonState(submitButton, isValid);
};

/**
 * Validates user input and updates UI state accordingly
 * @param {HTMLInputElement} input - The input element to validate
 * @param {string[]} existingPasskeyNames - Array of existing passkey names (lowercase)
 * @param {HTMLButtonElement} submitButton - Submit button to enable/disable
 * @param {HTMLElement} errorMessage - Error message element to show/hide
 * @returns {boolean} True if input is valid, false otherwise
 */
const validateInput = (input, existingPasskeyNames, submitButton, errorMessage) => {
    const error = validatePasskeyName(input.value, existingPasskeyNames);
    updateValidationUI(error, input, submitButton, errorMessage);
    return error === null;
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

        try {
            validateRequiredDOMElements({ modalElement, submitButton, cancelButton, input, errorMessage }); 
        } catch (error) {
            reject(error);
            return;
        }
        
        const modalInstance = initializeModal(modalElement, input, submitButton, userAccountContainer, errorMessage);

        // Define handlers inside promise scope
        let validationTimeout;
        const handleInput = () => {
            clearTimeout(validationTimeout);
            validationTimeout = setTimeout(() => {
                validateInput(input, existingPasskeyNames, submitButton, errorMessage);
            }, DEBOUNCE_TIME); // Short debounce time to reduce excessive validation calls
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
            displayToast('Passkey registration cancelled', messageType.info);
            resolve(null);
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

/**
 * Shows a confirmation modal with custom message and button text
 * @param {string} heading - The heading to display
 * @param {string} message - The message to display
 * @param {string} buttonText - Text for the confirmation button (default: "Continue")
 * @returns {Promise<boolean>} Promise that resolves to true when confirmed, false when cancelled
 */
export function showConfirmationModal(heading, message, buttonText = "Continue") {
    return new Promise((resolve) => {
        const modalId = `confirmation-modal-${Date.now()}`; // Unique ID
        
        // Sanitize user inputs individually
        const safeHeading = DOMPurify.sanitize(heading);
        const safeMessage = DOMPurify.sanitize(message);
        const safeButtonText = DOMPurify.sanitize(buttonText);
        
        // Create modal HTML with sanitized content
        const modalHTML = `
            <div id="${modalId}" class="modal">
                <div class="modal-content">
                    <h4>${safeHeading}</h4>
                    <p>${safeMessage}</p>
                </div>
                <div class="modal-footer">
                    <a href="#!" class="modal-close waves-effect waves-red btn-flat" id="${modalId}-cancel">Cancel</a>
                    <a href="#!" class="modal-close waves-effect waves-green btn" id="${modalId}-confirm">${safeButtonText}</a>
                </div>
            </div>
        `;

        // Remove any existing confirmation modals
        document.querySelectorAll('[id^="confirmation-modal-"]').forEach(modal => modal.remove());

        // Add modal to DOM
        document.body.insertAdjacentHTML('beforeend', modalHTML);
        
        const modalElement = document.getElementById(modalId);
        const confirmButton = document.getElementById(`${modalId}-confirm`);
        const cancelButton = document.getElementById(`${modalId}-cancel`);

        if (!modalElement || !confirmButton || !cancelButton) {
            console.error('Failed to create modal elements');
            resolve(false);
            return;
        }

        // Event handlers
        const handleConfirm = () => {
            cleanup();
            resolve(true);
        };

        const handleCancel = () => {
            cleanup();
            resolve(false);
        };

        const cleanup = () => {
            confirmButton.removeEventListener('click', handleConfirm);
            cancelButton.removeEventListener('click', handleCancel);
            modalInstance.destroy();
            modalElement.remove();
        };

        // Initialize modal
        const modalInstance = M.Modal.init(modalElement, {
            dismissible: false,
            onCloseEnd: cleanup
        });

        confirmButton.addEventListener('click', handleConfirm);
        cancelButton.addEventListener('click', handleCancel);

        // Open modal
        modalInstance.open();
    });
}