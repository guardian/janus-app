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
 * @param {Object} flashMessage Object containing flash messages by type
 */
export function displayFlashMessages(flashMessage) {
    if (!flashMessage) { 
        return 
    }
    if (flashMessage.success) {
        displayToast(flashMessage.success, messageType.success);
    }
    if (flashMessage.info) {
       displayToast(flashMessage.info, messageType.info);
    }
    if (flashMessage.error) {
        displayToast(flashMessage.error, messageType.error);
    }
}
