/**
 * Toast notification utilities for user feedback
 * 
 * This module provides functions for displaying toast notifications to users:
 * - Standard toast messages with different severity levels
 * - Flash message display from server responses
 * 
 * Features:
 * - Color-coded message types (error, warning, info, success)
 * - XSS protection via DOMPurify sanitization
 * - Integration with Materialize CSS toast component
 * - Support for interactive toasts with buttons and callbacks
 * - Server flash message processing and display
 * 
 * Message Types:
 * - error: Red notifications for errors and failures
 * - warning: Orange notifications for warnings and cautions
 * - info: Blue notifications for informational messages
 * - success: Green notifications for successful operations
 * 
 * Dependencies:
 * - DOMPurify: Content sanitization for security
 * - Materialize CSS: Toast component styling and behavior
 * 
 * @fileoverview Toast notification system for user feedback and messaging
 */

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
