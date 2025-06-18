import DOMPurify from 'dompurify';
import M from 'materialize-css';

/**
 * Displays flash messages from the server as toasts
 * @param {Object} flashMessages Object containing flash messages by type
 */
export function displayFlashMessages(flashMessages) {
    if (!flashMessages) { 
        return 
    }
    if (flashMessages.success) {
        M.toast({
            html: DOMPurify.sanitize(flashMessages.success),
            classes: 'green lighten-1 rounded',
        });
    }
    if (flashMessages.info) {
        M.toast({
            html: DOMPurify.sanitize(flashMessages.info),
            classes: 'blue lighten-1 rounded',
        });
    }
    if (flashMessages.error) {
        M.toast({
            html: DOMPurify.sanitize(flashMessages.error),
            classes: 'red lighten-1 rounded',
        });
    }
}
