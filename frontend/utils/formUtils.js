import DOMPurify from 'dompurify';

/**
 * Creates and submits a form with the provided data
 * @param {string} targetHref - Form submission URL
 * @param {Object} formData - Data to include in the form
 */
export function createAndSubmitForm(targetHref, formData) {
    const form = document.createElement('form');
    form.setAttribute('method', 'post');
    form.setAttribute('action', DOMPurify.sanitize(targetHref));

    Object.entries(formData).forEach(([name, value]) => {
        const input = document.createElement('input');
        input.setAttribute('type', 'hidden');
        input.setAttribute('name', DOMPurify.sanitize(name));
        input.setAttribute('value', DOMPurify.sanitize(value));
        form.appendChild(input);
    });

    document.body.append(form);
    form.submit();
}
