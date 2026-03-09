const $id = (select) => document.getElementById(select);
const $ = (select) => document.querySelector(select);
const $$ = (select) => document.querySelectorAll(select);

// Add event listener to one element (if it exists)
const on = (target, event, handler) => {
    const el = typeof target === 'string' ? document.querySelector(target) : target;
    if (el) el.addEventListener(event, handler);
};

// Add event listener to all matching elements (if any)
const onAll = (sel, event, handler) => {
    const els = $$(sel);
    if (els.length) els.forEach(el => el.addEventListener(event, handler));
};

const forAll = (sel, fn) => {
    const els = $$(sel);
    if (els.length) els.forEach(fn);
};

let $categoryToRename = null;
let $categoryToDelete = null;

const toastSuccess = 'toast-success';
const toastError = 'toast-error';
const poll = $id('poll-js')?.dataset ?? null;
const i18n = $id('i18n-js').dataset;
const generalError = i18n.error;
const bookmarkMovedSuccess = i18n.bookmarkMovedSuccess;
const categoryDeletedSuccess = i18n.categoryDeletedSuccess;
const categoryRenamedSuccess = i18n.categoryRenamedSuccess;
const trashEmptiedSuccess = i18n.trashEmptiedSuccess;
const bookmarkDeletedSuccess = i18n.bookmarkDeletedSuccess;
const categoryCreatedSuccess = i18n.categoryCreatedSuccess;
const bookmarkCreatedSuccess = i18n.bookmarkCreatedSuccess;
const logoutDevicesSuccess = i18n.logoutDevicesSuccess;

function openModal(e) {
    e.classList.add('is-active');

    if (e.id === 'add-category-modal') {
        setTimeout(() => {
            $id('category').focus();
        }, 100);
    }
}

function closeModal(e) {
    e.classList.remove('is-active');
}

function closeAllModals() {
    forAll('.modal', closeModal);
}

function showLoading(e) {
    const button = $id(e);
    if (button) {
        button.classList.add('is-loading');
        button.disabled = true;
    }
}

function handleAddClick() {
    openModal($id('add-category-modal'));
}

function handleDragStart(e) {
    const item = e.currentTarget;
    const dragIcon = document.createElement('div');
    dragIcon.className = 'drag-icon';
    dragIcon.innerHTML = '<i class="fas fa-bookmark fa-2x"></i>';
    dragIcon.style.position = 'absolute';
    dragIcon.style.top = '-1000px';
    dragIcon.style.color = '#3273dc';

    document.body.appendChild(dragIcon);
    e.dataTransfer.setDragImage(dragIcon, 25, 25);

    item.closest('.card')?.classList.add('dragging');
    e.dataTransfer.setData('text/plain', item.dataset.uid);

    setTimeout(() => {
        document.body.removeChild(dragIcon);
    }, 0);
}

function handleDragEnd(e) {
    const item = e.currentTarget;
    item.closest('.card')?.classList.remove('dragging');
}

function handleDragOver(e) {
    e.preventDefault();
    e.currentTarget.classList.add('drag-over');
}

function handleDragLeave(e) {
    e.currentTarget.classList.remove('drag-over');
}

function handleDrop(e) {
    e.preventDefault();
    const target = e.currentTarget;
    target.classList.remove('drag-over');

    const uid = e.dataTransfer.getData('text/plain');
    const categoryUid = target.dataset.uid;

    window.apiPut("/api/v1/items", {
        uid: uid,
        category: categoryUid
    })
        .then(() => {
            sessionStorage.setItem(toastSuccess, bookmarkMovedSuccess);
        })
        .catch((error) => {
            console.log(error);
            sessionStorage.setItem(toastError, generalError);
        })
        .finally(() => {
            window.location.href = "/dashboard";
        });
}

function handleCardTrashClick(e) {
    e.preventDefault();
    e.stopPropagation();

    deleteItem(e.currentTarget.closest('.card'));
}

function handleDeleteAccountClick(e) {
    e.preventDefault();
    e.stopPropagation();
    openModal($id('delete-account-modal'));
}

function handleLogoutDevicesClick(e) {
    e.preventDefault();
    e.stopPropagation();
    openModal($id('logout-devices-confirm-modal'));
}

function handleCategoryTrashClick(e) {
    e.preventDefault();
    e.stopPropagation();

    $categoryToDelete = e.currentTarget;
    openModal($id('delete-category-confirm-modal'));
}

function handleCategoryRenameClick(e) {
    e.preventDefault();
    e.stopPropagation();

    $categoryToRename = e.currentTarget;
    $id('existing-category').value = $categoryToRename.dataset.name;
    openModal($id('rename-category-modal'));
}

function handleConfirmCategoryDelete() {
    if ($categoryToDelete) {
        const uid = $categoryToDelete.dataset.uid;

        window.apiDelete(`/api/v1/categories/${uid}`)
            .then(() => {
                closeAllModals();
                sessionStorage.setItem(toastSuccess, categoryDeletedSuccess);
            })
            .catch((error) => {
                console.log(error);
                sessionStorage.setItem(toastError, generalError);
            })
            .finally(() => {
                closeAllModals();
                window.location.href = "/dashboard";
            });
    }
}

function handleLogoutDevices() {
    window.apiPost("/dashboard/profile/logout-devices", {})
        .then(() => {
            closeAllModals();
            sessionStorage.setItem(toastSuccess, logoutDevicesSuccess);
        })
        .catch((error) => {
            console.log(error);
            sessionStorage.setItem(toastError, generalError);
        })
        .finally(() => {
            window.location.href = "/dashboard/profile";
        });
}

function handleAddCategory(e) {
    e.preventDefault();

    const categoryInput = $id('category');
    const category = categoryInput?.value;

    if (category) {
        window.apiPost("/api/v1/categories", {
            name: category
        })
            .then(() => {
                sessionStorage.setItem(toastSuccess, categoryCreatedSuccess);
                categoryInput.value = '';
                closeAllModals();
            })
            .catch((error) => {
                console.log(error);
                sessionStorage.setItem(toastError, generalError);
            })
            .finally(() => {
                window.location.href = "/dashboard";
            });
    } else {
        categoryInput?.classList.add('is-danger');
    }
}

function handleRenameCategory(e) {
    e.preventDefault();

    const categoryInput = $id('existing-category');
    const uid = $categoryToRename.dataset.uid;
    const category = categoryInput?.value;

    if (category) {
        window.apiPut("/api/v1/categories", {
            uid: uid,
            name: category
        })
            .then(() => {
                sessionStorage.setItem(toastSuccess, categoryRenamedSuccess);
                categoryInput.value = '';
                closeAllModals();
            })
            .catch((error) => {
                console.log(error);
                sessionStorage.setItem(toastError, generalError);
            })
            .finally(() => {
                window.location.href = "/dashboard/" + uid;
            });
    } else {
        categoryInput?.classList.add('is-danger');
    }
}

function confirmEmptyTrash() {
    window.apiDelete("/api/v1/items/trash")
        .then(() => {
            closeAllModals();
            sessionStorage.setItem(toastSuccess, trashEmptiedSuccess);
        })
        .catch((error) => {
            console.log(error);
            sessionStorage.setItem(toastError, generalError);
        })
        .finally(() => {
            window.location.href = "/dashboard";
        });
}

function deleteItem(card) {
    card.style.transition = 'all 0.3s ease';
    card.style.opacity = '0';

    setTimeout(() => {
        card.closest('.column').remove();
    }, 300);

    const uid = card.dataset.uid;
    const category = card.dataset.category;

    window.apiPut(`/api/v1/items/${uid}`, {})
        .then(() => {
            closeAllModals();
            sessionStorage.setItem(toastSuccess, bookmarkDeletedSuccess);
        })
        .catch((error) => {
            console.log(error);
            sessionStorage.setItem(toastError, error);
        })
        .finally(() => {
            window.location.href = "/dashboard/" + category;
        });
}

function emptyTrash(e) {
    e.preventDefault();
    e.stopPropagation();
    openModal($id('empty-trash-confirm-modal'));
}

function clearUrlError() {
    $id('bookmark-url')?.classList.remove('is-danger');
}

function showToast(message, type = 'success', duration = 3000) {
    const toastContainer = document.querySelector('.toast-container');
    if (!toastContainer) return;

    const toast = document.createElement('div');
    toast.className = `toast ${type === 'error' ? 'is-danger' : ''}`;
    toast.innerHTML = `
        <span class="icon">
            <i class="fas ${type === 'error' ? 'fa-exclamation-circle' : 'fa-check-circle'}"></i>
        </span>
        <span>${message}</span>
    `;

    toastContainer.appendChild(toast);
    toast.offsetHeight;

    requestAnimationFrame(() => {
        toast.classList.add('is-active');
    });

    setTimeout(() => {
        toast.classList.remove('is-active');
        setTimeout(() => {
            toastContainer.removeChild(toast);
        }, 300);
    }, duration);
}

function addBookmarkModal() {
    openModal($id('add-bookmark-modal'));
}

function handleToastsOnLoad() {
    const success = sessionStorage.getItem(toastSuccess);
    if (success) {
        showToast(success);
        sessionStorage.removeItem(toastSuccess);
    }

    const error = sessionStorage.getItem(toastError);
    if (error) {
        showToast(error, "error");
        sessionStorage.setItem(toastError, "");
    }
}

function addBookmark(e) {
    e.preventDefault();
    e.stopPropagation();

    const url = $id('bookmark-url').value;
    const category = $id('bookmark-category').value;
    const confirmAddBookmark = $id('confirm-add-bookmark');

    if (url && category) {
        confirmAddBookmark.classList.add('is-loading');
        confirmAddBookmark.disabled = true;

        window.apiPost('/api/v1/items', {
            url: url,
            category: category
        })
            .then(() => {
                sessionStorage.setItem(toastSuccess, bookmarkCreatedSuccess);

                closeAllModals();
                window.location.href = "/dashboard/" + category;
            })
            .catch((error) => {
                console.log(error);
                sessionStorage.setItem(toastError, generalError);
            })
            .finally(() => {
                confirmAddBookmark.classList.remove('is-loading');
                confirmAddBookmark.disabled = false;
            });
    }
}

function search(e) {
    const searchTerm = e?.target?.value.toLowerCase() || '';
    const cards = $$('.card');

    cards.forEach(card => {
        const title = card.querySelector('.card-title')?.textContent.toLowerCase() || '';
        const column = card.closest('.column');

        if (column) {
            if (!searchTerm) {
                column.style.display = '';
            } else {
                column.style.display = title.includes(searchTerm) ? '' : 'none';
            }
        }
    });
}

function handleKeyNavigation(event) {
    if (event.key === "Enter") {
        const openModalEl = $('.modal.is-active');
        if (!openModalEl) return;

        const confirmButton = openModalEl.querySelector('[data-confirm]');
        if (confirmButton) {
            event.preventDefault();
            confirmButton.click();
        }
    }

    if (event.code === "Escape") {
        closeAllModals();
    }
}

function setupAutoFocusNext(selector) {
    const inputs = document.querySelectorAll(selector);
    inputs.forEach((input, index) => {
        on(input, 'input', (e) => {
            const value = e.target.value;
            e.target.value = value.replace(/[^0-9]/g, '').slice(0, 1);
            if (e.target.value && index < inputs.length - 1) {
                inputs[index + 1].focus();
            }
        });
    });
}

function focusFirstVisibleInput(selector) {
    const inputs = document.querySelectorAll(selector);
    for (let input of inputs) {
        if (input.offsetParent !== null) {
            input.focus();
            break;
        }
    }
}

async function polling() {
    try {
        const count = $$('.card').length;
        const match = window.location.pathname.match(/\/dashboard\/(.+)/);
        const uid = match ? match[1] : null;

        const response = await window.apiPostNoThrow('/api/v1/categories/poll', {
            count: count.toString(),
            category: uid
        });

        if (response.status === 200) {
            location.reload();
        }

        setTimeout(polling, 3000);
    } catch (error) {
        console.log(error);
        setTimeout(polling, 3000);
    }
}

if (poll != null && poll.poll === "true") {
    polling();
}

// Theme Toggle with localStorage
class ThemeManager {
    constructor() {
        this.theme = this.getStoredTheme();
        this.init();
    }

    init() {
        this.applyTheme(this.theme);
        this.createToggleButton();
    }

    getStoredTheme() {
        const stored = localStorage.getItem('theme');
        if (stored && (stored === 'light' || stored === 'dark')) {
            return stored;
        }

        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            return 'dark';
        }

        return 'light';
    }

    applyTheme(theme) {
        const html = document.documentElement;

        if (theme === 'dark') {
            html.setAttribute('data-theme', 'dark');
            html.classList.add('theme-dark');
        } else {
            html.removeAttribute('data-theme');
            html.classList.remove('theme-dark');
        }

        this.theme = theme;
        localStorage.setItem('theme', theme);
    }

    toggleTheme() {
        const newTheme = this.theme === 'light' ? 'dark' : 'light';
        this.applyTheme(newTheme);
        this.updateToggleButton();
    }

    createToggleButton() {
        const existingToggle = document.getElementById('theme-toggle');
        if (existingToggle) {
            existingToggle.remove();
        }

        const toggle = document.createElement('button');
        toggle.id = 'theme-toggle';
        toggle.className = 'theme-toggle-button';
        toggle.innerHTML = this.getToggleIcon();
        toggle.setAttribute('aria-label', `Switch to ${this.theme === 'light' ? 'dark' : 'light'} mode`);
        toggle.setAttribute('title', `Switch to ${this.theme === 'light' ? 'dark' : 'light'} mode`);

        toggle.addEventListener('click', () => this.toggleTheme());

        document.body.appendChild(toggle);
    }

    updateToggleButton() {
        const toggle = document.getElementById('theme-toggle');
        if (toggle) {
            toggle.innerHTML = this.getToggleIcon();
            toggle.setAttribute('aria-label', `Switch to ${this.theme === 'light' ? 'dark' : 'light'} mode`);
            toggle.setAttribute('title', `Switch to ${this.theme === 'light' ? 'dark' : 'light'} mode`);
        }
    }

    getToggleIcon() {
        return this.theme === 'light'
            ? '🌙'
            : '☀️';
    }
}

let themeToggle;

document.addEventListener('DOMContentLoaded', () => {
    themeToggle = new ThemeManager();
});

function toggleTheme() {
    if (themeToggle) {
        themeToggle.toggleTheme();
    }
}

focusFirstVisibleInput('.otp-input');
setupAutoFocusNext('.otp-input');
on(document, 'keydown', handleKeyNavigation);
on(window, 'load', handleToastsOnLoad);
on('#add-category-button', 'click', handleAddClick);
on('#add-category-submit', 'click', handleAddCategory);
on('#rename-category-submit', 'click', handleRenameCategory);
on('#confirm-empty-trash', 'click', confirmEmptyTrash);
on('#bookmark-url', 'input', clearUrlError);
on('#add-bookmark', 'click', addBookmarkModal);
on('#search-input', 'input', search);
on('#confirm-add-bookmark', 'click', addBookmark);
on('#confirm-logout-devices', 'click', handleLogoutDevices);
on('#logout-devices', 'click', handleLogoutDevicesClick);
on('#delete-account', 'click', handleDeleteAccountClick);
on('#confirm-category-delete', 'click', handleConfirmCategoryDelete);
onAll('.category-trash', 'click', handleCategoryTrashClick);
onAll('.category-rename', 'click', handleCategoryRenameClick);
onAll('.card-trash', 'click', handleCardTrashClick);
onAll('.modal-background, .modal-card-head .delete, .modal-card-foot .button:not(.is-danger)', 'click', closeAllModals);
onAll('.empty-trash', 'click', emptyTrash);
forAll('.dragging[draggable="true"]', (item) => {
    item.addEventListener('dragstart', handleDragStart);
    item.addEventListener('dragend', handleDragEnd);
});
forAll('.menu-list a[data-category]', (target) => {
    target.addEventListener('dragover', handleDragOver);
    target.addEventListener('dragleave', handleDragLeave);
    target.addEventListener('drop', handleDrop);
});
