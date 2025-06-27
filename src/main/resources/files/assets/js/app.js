let $cardToDelete = null;
const toastSuccess = 'toast-success';
const toastError = 'toast-error';
const error = document.getElementById('i18n-js').dataset.error;
const bookmarkMovedSuccess = document.getElementById('i18n-js').dataset.bookmarkMovedSuccess;
const categoryDeletedSuccess = document.getElementById('i18n-js').dataset.categoryDeletedSuccess;
const trashEmptiedSuccess = document.getElementById('i18n-js').dataset.trashEmptiedSuccess;
const bookmarkDeletedSuccess = document.getElementById('i18n-js').dataset.bookmarkDeletedSuccess;
const categoryCreatedSuccess = document.getElementById('i18n-js').dataset.categoryCreatedSuccess;
const bookmarkCreatedSuccess = document.getElementById('i18n-js').dataset.bookmarkCreatedSuccess;
const logoutDevicesSuccess = document.getElementById('i18n-js').dataset.logoutDevicesSuccess;
const searchInput = document.getElementById('searchInput');
const $addButton = document.getElementById('add-category-button');
const $addBookmark = document.getElementById('add-bookmark-modal');
const $modal = document.getElementById('add-category-modal');
const $deleteModal = document.getElementById('delete-confirm-modal');
const $deleteAccountModal = document.getElementById('delete-account-modal');
const $logoutDevicesModal = document.getElementById('logout-devices-confirm-modal');
const $deleteCategoryModal = document.getElementById('delete-category-confirm-modal');
const $emptyTrashModal = document.getElementById('empty-trash-confirm-modal');
const $closeButtons = document.querySelectorAll('.modal-background, .modal-card-head .delete, .modal-card-foot .button:not(.is-danger)');
const draggableItems = document.querySelectorAll('.dragging[draggable="true"]');
const categoryTargets = document.querySelectorAll('.menu-list a[data-category]');
const $addBookmarkSubmit = document.getElementById('add-category-submit');
const $confirmAddBookmark = document.getElementById('confirm-add-bookmark');
const deleteAccount = document.getElementById('delete-account');
const logoutDevices = document.getElementById('logout-devices');
const $bookmarkUrl = document.getElementById('bookmark-url');
const fabButton = document.getElementById('fab-add-bookmark');
const applicationJson = "application/json";

function openModal($el) {
    $el.classList.add('is-active');

    if ($el.id === 'add-category-modal') {
        setTimeout(() => {
            document.getElementById('category').focus();
        }, 100);
    }
}

function closeModal($el) {
    $el.classList.remove('is-active');
}

function closeAllModals() {
    document.querySelectorAll('.modal').forEach(($modal) => {
        closeModal($modal);
    });
}

function showLoading(element) {
    console.log("element: " + element);
    const button = document.getElementById(element);
    if (button) {
        button.classList.add('is-loading');
        button.disabled = true;
    }
}

$addButton.addEventListener('click', () => {
    openModal($modal);
});

draggableItems.forEach(item => {
    item.addEventListener('dragstart', (e) => {
        const dragIcon = document.createElement('div');
        dragIcon.className = 'drag-icon';
        dragIcon.innerHTML = '<i class="fas fa-bookmark fa-2x"></i>';
        dragIcon.style.position = 'absolute';
        dragIcon.style.top = '-1000px';
        dragIcon.style.color = '#3273dc';
        document.body.appendChild(dragIcon);

        e.dataTransfer.setDragImage(dragIcon, 25, 25);

        item.closest('.card').classList.add('dragging');
        e.dataTransfer.setData('text/plain', item.dataset.uid);

        setTimeout(() => {
            document.body.removeChild(dragIcon);
        }, 0);
    });

    item.addEventListener('dragend', () => {
        item.closest('.card').classList.remove('dragging');
    });
});

categoryTargets.forEach(target => {
    target.addEventListener('dragover', (e) => {
        e.preventDefault();
        target.classList.add('drag-over');
    });

    target.addEventListener('dragleave', () => {
        target.classList.remove('drag-over');
    });

    target.addEventListener('drop', async (e) => {
        e.preventDefault();
        target.classList.remove('drag-over');

        const uid = e.dataTransfer.getData('text/plain');
        const categoryUid = target.dataset.uid;

        const response = await fetch("/api/v1/items", {
            method: "PUT",
            body: JSON.stringify({
                uid: uid,
                category: categoryUid
            }),
            headers: {
                "Content-type" : applicationJson
            }
        });

        if (response.ok) {
            sessionStorage.setItem(toastSuccess, bookmarkMovedSuccess);
        } else {
            sessionStorage.setItem(toastError, error);
        }
        window.location.href = "/dashboard";
    });
});

document.querySelectorAll('.card-trash').forEach(trashIcon => {
    trashIcon.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        $cardToDelete = e.currentTarget.closest('.card');
        openModal($deleteModal);
    });
});

if (deleteAccount) {
    deleteAccount.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        openModal($deleteAccountModal);
    });
}

if (logoutDevices) {
    logoutDevices.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        openModal($logoutDevicesModal);
    });
}

document.querySelectorAll('.category-trash').forEach(trashIcon => {
    trashIcon.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        $categoryToDelete = e.currentTarget;
        openModal($deleteCategoryModal);
    });
});

document.querySelectorAll('.empty-trash').forEach(trashIcon => {
    trashIcon.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        openModal($emptyTrashModal);
    });
});

document.getElementById('confirm-category-delete').addEventListener('click', async () => {
    if ($categoryToDelete) {
        const uid = $categoryToDelete.dataset.uid;
        const response = await fetch("/api/v1/categories/" + uid, {
            method: "DELETE",
            headers: {
                "Content-type" : applicationJson
            }
        });

        if (response.ok) {
            sessionStorage.setItem(toastSuccess, categoryDeletedSuccess);
            closeAllModals();
        } else {
            sessionStorage.setItem(toastError, error);
        }

        window.location.href = "/dashboard";
    }
    closeModal($deleteCategoryModal);
});

document.getElementById('confirm-logout-devices').addEventListener('click', async () => {
    const response = await fetch("/dashboard/profile/logout-devices", {
        method: "POST",
    });

    if (response.ok) {
        sessionStorage.setItem(toastSuccess, logoutDevicesSuccess);
        closeAllModals();
    } else {
        sessionStorage.setItem(toastError, error);
    }

    window.location.href = "/dashboard/profile";
});

document.getElementById('confirm-empty-trash').addEventListener('click', async () => {
    const response = await fetch("/api/v1/items/trash", {
        method: "DELETE",
        headers: {
            "Content-type" : applicationJson
        }
    });

    if (response.ok) {
        closeAllModals();
        sessionStorage.setItem(toastSuccess, trashEmptiedSuccess);
    } else {
        sessionStorage.setItem(toastError, error);
    }

    window.location.href = "/dashboard";
});

document.getElementById('confirm-delete').addEventListener('click', async () => {
    if ($cardToDelete) {
        $cardToDelete.style.transition = 'all 0.3s ease';
        $cardToDelete.style.opacity = '0';
        setTimeout(() => {
            $cardToDelete.closest('.column').remove();
        }, 300);

        const uid = $cardToDelete.dataset.uid;
        const category = $cardToDelete.dataset.category;
        const response = await fetch("/api/v1/items/" + uid, {
            method: "PUT",
            headers: {
                "Content-type" : applicationJson
            }
        });

        if (response.ok) {
            closeAllModals();
            sessionStorage.setItem(toastSuccess, bookmarkDeletedSuccess);
        } else {
            sessionStorage.setItem(toastError, error);
        }

        window.location.href = "/dashboard/" + category;
    }
});

if ($addBookmarkSubmit) {
    $addBookmarkSubmit.addEventListener('click', async (e) => {
        e.preventDefault();

        const category = document.getElementById('category').value;
        if (category) {
            const response = await fetch("/api/v1/categories", {
                method: "POST",
                body: JSON.stringify({
                    name: category
                }),
                headers: {
                    "Content-type" : applicationJson
                }
            });

            if (response.ok) {
                sessionStorage.setItem(toastSuccess, categoryCreatedSuccess);
                document.getElementById('category').value = '';
                closeAllModals();
                window.location.href = "/dashboard";
            } else {
                sessionStorage.setItem(toastError, error);
            }
        } else {
            document.getElementById('category').classList.add('is-danger');
        }
    });
}

if ($bookmarkUrl) {
    $bookmarkUrl.addEventListener('input', () => {
        $bookmarkUrl.classList.remove('is-danger');
    });
}

// Toast functionality
function showToast(message, type = 'success', duration = 3000) {
    const toastContainer = document.querySelector('.toast-container');
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

window.addEventListener('load', () => {
    const success = sessionStorage.getItem(toastSuccess);
    if (success) {
        showToast(success);
        sessionStorage.setItem(toastSuccess, "");
    }

    const error = sessionStorage.getItem(toastError);
    if (error) {
        showToast(error, "error");
        sessionStorage.setItem(toastError, "");
    }
});


if (fabButton) {
    fabButton.addEventListener('click', () => {
        openModal($addBookmark);
    });
}

if ($confirmAddBookmark) {
    $confirmAddBookmark.addEventListener('click', async (e) => {
        e.preventDefault();
        e.stopPropagation();

        const url = document.getElementById('bookmark-url').value;
        const category = document.getElementById('bookmark-category').value;

        if (url && category) {
            $confirmAddBookmark.classList.add('is-loading');
            $confirmAddBookmark.disabled = true;

            try {
                const response = await fetch('/api/v1/items', {
                    method: 'POST',
                    headers: {
                        "Content-type": "application/json" // fix here
                    },
                    body: JSON.stringify({
                        url: url,
                        category: category
                    })
                });

                if (response.ok) {
                    sessionStorage.setItem(toastSuccess, bookmarkCreatedSuccess);

                    closeAllModals();
                    window.location.href = "/dashboard/" + category;
                } else {
                    sessionStorage.setItem(toastError, error);
                }
            } catch (err) {
                sessionStorage.setItem(toastError, error);
            } finally {
                $confirmAddBookmark.classList.remove('is-loading');
                $confirmAddBookmark.disabled = false;
            }
        }
    });
}

if (searchInput) {
    searchInput.addEventListener('input', function(e) {
        const searchTerm = e.target.value.toLowerCase();
        const cards = document.querySelectorAll('.card');

        cards.forEach(card => {
            const title = card.querySelector('.card-title').textContent.toLowerCase();
            if (title.includes(searchTerm)) {
                card.closest('.column').style.display = '';
            } else {
                card.closest('.column').style.display = 'none';
            }
        });
    });
}

document.addEventListener("keydown", function(event) {
    if (event.key === "Enter") {
        const openModal = document.querySelector(".modal.is-active");
        if (!openModal) return;

        const confirmButton = openModal.querySelector("[data-confirm]");
        if (confirmButton) {
            event.preventDefault();
            confirmButton.click();
        }
    }

    if (event.code === 'Escape') {
        closeAllModals();
    }
});

async function poll() {
    try {
        let count = document.querySelectorAll('.card');

        const path = window.location.pathname;
        const match = path.match(/\/dashboard\/(.+)/);
        const uid = match ? match[1] : null;

        const response = await fetch("/api/v1/categories/poll", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ "count": count.length.toString(), "category" : uid })
        });

        if (response.ok) {
            location.reload();
        }

        setTimeout(poll, 3000);
    } catch (error) {}
}

$closeButtons.forEach(($close) => {
    $close.addEventListener('click', () => {
        closeAllModals();
    });
});

poll();