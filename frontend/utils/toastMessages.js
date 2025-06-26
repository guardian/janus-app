import DOMPurify from 'dompurify';
import M from 'materialize-css';



export const messageType = {
    error: 'red',
    warning: 'orange',
    info: 'blue',
    success: 'green'
} 


/**
 * Displays messages as toasts
 * @param {string} message user message
 * @param {string} type messageType
 */
export function displayToast(message, type) {
    M.toast({
            html: DOMPurify.sanitize(message),
            classes: `rounded ${type}`,
    });
}

/**
 * Displays flash messages from the server as toasts
 * @param {Object} flashMessages Object containing flash messages by type
 */
export function displayFlashMessages(flashMessages) {
    if (!flashMessages) { 
        return 
    }
    if (flashMessages.success) {
        displayToast(flashMessages.success, messageType.success);
    }
    if (flashMessages.info) {
       displayToast(flashMessages.info, messageType.info);
    }
    if (flashMessages.error) {
        displayToast(flashMessages.error, messageType.error);
    }
}
