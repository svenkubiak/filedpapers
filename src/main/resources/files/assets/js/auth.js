function showLoading() {
    const button = document.getElementById('loading-button');
    if (button) {
        button.classList.add('is-loading');
        button.disabled = true;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const notification = document.querySelector('.notification');
    const deleteButton = notification.querySelector('.delete');

    deleteButton.addEventListener('click', () => {
        notification.remove();
    });
});

const username = document.getElementById("username");
if (username) {
    username.focus();
}