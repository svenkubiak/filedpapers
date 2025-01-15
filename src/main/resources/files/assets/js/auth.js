function showLoading(element) {
    const button = document.getElementById(element);
    if (button) {
        button.classList.add('is-loading');
        button.disabled = true;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const notification = document.querySelector('.notification');
    if (notification) {
        const deleteButton = notification.querySelector('.delete');

        deleteButton.addEventListener('click', () => {
            notification.remove();
        });
    }
});

const username = document.getElementById("username");
if (username) {
    username.focus();
}