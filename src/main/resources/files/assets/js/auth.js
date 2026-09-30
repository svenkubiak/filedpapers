/*
 * Filed Papers — sign in, sign up and password reset.
 *
 * These pages are plain form posts; the only thing script does here is keep a
 * submitted button from being pressed twice and put the cursor in the field the
 * page is about.
 */
document.querySelectorAll('form[data-busy]').forEach(form => {
    form.addEventListener('submit', () => {
        const button = document.getElementById(form.dataset.busy);
        if (button) {
            button.classList.add('is-busy');
            button.disabled = true;
        }
    });
});

const firstField = document.getElementById('mfa') || document.getElementById('username');
firstField?.focus();
