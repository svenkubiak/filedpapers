document.querySelectorAll('form[data-busy]').forEach(form => {
    form.addEventListener('submit', () => {
        const button = document.getElementById(form.dataset.busy);
        if (button) {
            button.classList.add('is-busy');
            button.disabled = true;
        }
    });
});

document.querySelectorAll('.secret__toggle').forEach(button => {
    button.addEventListener('click', () => {
        const input = button.parentElement?.querySelector('input');
        if (!input) return;
        const visible = input.type === 'password';
        input.type = visible ? 'text' : 'password';
        button.setAttribute('aria-pressed', String(visible));
        button.setAttribute('aria-label', visible ? button.dataset.hide : button.dataset.show);
        button.querySelector('use')?.setAttribute('href', visible ? '#i-eye-off' : '#i-eye');
    });
});

const firstField = document.getElementById('mfa') || document.getElementById('username');
firstField?.focus();
