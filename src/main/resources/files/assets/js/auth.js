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
