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
const toastSuccess = 'toast-success';
const toastError = 'toast-error';
const poll = $id('poll-js').dataset;
const i18n = $id('i18n-js').dataset;
const generalError = i18n.error;
const archivedSuccess = i18n.archivedSuccess;
const bookmarkMovedSuccess = i18n.bookmarkMovedSuccess;
const categoryDeletedSuccess = i18n.categoryDeletedSuccess;
const categoryRenamedSuccess = i18n.categoryRenamedSuccess;
const trashEmptiedSuccess = i18n.trashEmptiedSuccess;
const bookmarkDeletedSuccess = i18n.bookmarkDeletedSuccess;
const categoryCreatedSuccess = i18n.categoryCreatedSuccess;
const bookmarkCreatedSuccess = i18n.bookmarkCreatedSuccess;
const logoutDevicesSuccess = i18n.logoutDevicesSuccess;

const csrfToken = $id('x-csrf-token')?.dataset?.csrfToken ?? "";
axios.defaults.headers.common['x-csrf-token'] = csrfToken;

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

    axios.put("/api/v1/items",{
        uid: uid,
        category: categoryUid
    } )
        .then(function (response) {
            sessionStorage.setItem(toastSuccess, bookmarkMovedSuccess);
        })
        .catch(function (error) {
            console.log(error);
            sessionStorage.setItem(toastError, generalError);
        })
        .finally(function () {
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

        axios.delete("/api/v1/categories/" + uid)
            .then(function (response) {
                closeAllModals();
                sessionStorage.setItem(toastSuccess, categoryDeletedSuccess);
            })
            .catch(function (error) {
                console.log(error);
                sessionStorage.setItem(toastError, generalError);
            })
            .finally(function () {
                closeAllModals();
                window.location.href = "/dashboard";
            });
    }
}

function handleCardArchiveClick(e) {
    const uid = e.currentTarget.dataset.uid;

    axios.post("/api/v1/archive/" + uid)
        .then(function (response) {
            showToast(archivedSuccess);
        })
        .catch(function (error) {
            console.log(error);
            showToast(generalError);
        })
}

function handleLogoutDevices() {
    axios.post("/dashboard/profile/logout-devices")
        .then(function (response) {
            closeAllModals();
            sessionStorage.setItem(toastSuccess, logoutDevicesSuccess);
        })
        .catch(function (error) {
            console.log(error);
            sessionStorage.setItem(toastError, generalError);
        })
        .finally(function () {
            window.location.href = "/dashboard/profile";
        });
}

function handleAddCategory(e) {
    e.preventDefault();

    const categoryInput = $id('category');
    const category = categoryInput?.value;

    if (category) {
        axios.post("/api/v1/categories", {
            name: category
        })
            .then(function (response) {
                sessionStorage.setItem(toastSuccess, categoryCreatedSuccess);
                categoryInput.value = '';
                closeAllModals();
            })
            .catch(function (error) {
                console.log(error);
                sessionStorage.setItem(toastError, generalError);
            })
            .finally(function () {
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
        axios.put("/api/v1/categories", {
            uid: uid,
            name: category
        })
            .then(function (response) {
                sessionStorage.setItem(toastSuccess, categoryRenamedSuccess);
                categoryInput.value = '';
                closeAllModals();
            })
            .catch(function (error) {
                console.log(error);
                sessionStorage.setItem(toastError, generalError);
            })
            .finally(function () {
                window.location.href = "/dashboard/" + uid;
            });
    } else {
        categoryInput?.classList.add('is-danger');
    }
}

function confirmEmptyTrash() {
    axios.delete("/api/v1/items/trash")
        .then(function (response) {
            closeAllModals();
            sessionStorage.setItem(toastSuccess, trashEmptiedSuccess);
        })
        .catch(function (error) {
            console.log(error);
            sessionStorage.setItem(toastError, generalError);
        })
        .finally(function () {
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

    axios.put("/api/v1/items/" + uid)
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

        axios.post('/api/v1/items',
            {
                url: url,
                category: category
            })
            .then(function (response) {
                sessionStorage.setItem(toastSuccess, bookmarkCreatedSuccess);

                closeAllModals();
                window.location.href = "/dashboard/" + category;
            })
            .catch(function (error) {
                console.log(error);
                sessionStorage.setItem(toastError, generalError);
            })
            .finally(function () {
                confirmAddBookmark.classList.remove('is-loading');
                confirmAddBookmark.disabled = false;
            });
    }
}

function search(e) {
    if (!e?.target?.value) return;

    const searchTerm = e.target.value.toLowerCase();
    const cards = $$('.card');

    cards.forEach(card => {
        const title = card.querySelector('.card-title')?.textContent.toLowerCase() || '';
        const column = card.closest('.column');

        if (column) {
            column.style.display = title.includes(searchTerm) ? '' : 'none';
        }
    });
}

function handleKeyNavigation(event) {
    if (event.key === "Enter") {
        const openModal = $('.modal.is-active');
        if (!openModal) return;

        const confirmButton = openModal.querySelector('[data-confirm]');
        if (confirmButton) {
            event.preventDefault();
            confirmButton.click();
        }
    }

    if (event.code === "Escape") {
        closeAllModals();
    }
}

function openPopup(e) {
    const uid = e.currentTarget.dataset.uid;

    const popup = window.open(
        '/media/archive/' + uid,
        'Archived',
        'width=1280,height=720,left=100,top=100,resizable=no,scrollbars=yes,menubar=no,toolbar=no,location=no,noopener,noreferrer'
    );
}

function setupAutoFocusNext(selector) {
    forAll(selector, (input, index, inputs) => {
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
onAll('.open-archive', 'click', openPopup);
onAll('.category-trash', 'click', handleCategoryTrashClick);
onAll('.category-rename', 'click', handleCategoryRenameClick);
onAll('.card-trash', 'click', handleCardTrashClick);
onAll('.card-archive', 'click', handleCardArchiveClick);
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

async function polling() {
    try {
        const count = $$('.card').length;
        const match = window.location.pathname.match(/\/dashboard\/(.+)/);
        const uid = match ? match[1] : null;

        axios.post('/api/v1/categories/poll',
            {
                count: count.toString(),
                category: uid
            },
            {
                validateStatus: function (status) {
                    return status === 200 || status === 304;
                }
            }
        )
            .then(function (response) {
                if (response.status === 200) {
                    location.reload();
                }
            })
            .catch(function (error) {
                console.log(error);
            });

        setTimeout(polling, 3000);
    } catch (error) {
        console.log(error);
    }
}

if (poll != null && poll.poll === "true") {
    polling();
}