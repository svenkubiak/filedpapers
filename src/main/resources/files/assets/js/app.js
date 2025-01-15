let $cardToDelete = null;
const toastSuccess = 'toast-success';
const toastError = 'toast-error';
const error = "Ops, something went wrong. Please try again.";
const $addButton = document.getElementById('add-category-button');
const $addBookmark = document.getElementById('add-bookmark-modal');
const $modal = document.getElementById('add-category-modal');
const $deleteModal = document.getElementById('delete-confirm-modal');
const $deleteAccountModal = document.getElementById('delete-account-modal');
const $deleteCategoryModal = document.getElementById('delete-category-confirm-modal');
const $emptyTrashModal = document.getElementById('empty-trash-confirm-modal');
const $closeButtons = document.querySelectorAll('.modal-background, .modal-card-head .delete, .modal-card-foot .button:not(.is-danger)');
const draggableItems = document.querySelectorAll('.dragging[draggable="true"]');
const categoryTargets = document.querySelectorAll('.menu-list a[data-category]');
const $addBookmarkSubmit = document.getElementById('add-category-submit');
const $confirmAddBookmark = document.getElementById('confirm-add-bookmark');
const deleteAccount = document.getElementById('delete-account');
const $bookmarkUrl = document.getElementById('bookmark-url');
const fabButton = document.getElementById('fab-add-bookmark');

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

$closeButtons.forEach(($close) => {
    $close.addEventListener('click', () => {
        closeAllModals();
    });
});

document.addEventListener('keydown', (event) => {
    if (event.code === 'Escape') {
        closeAllModals();
    }
});

draggableItems.forEach(item => {
    item.addEventListener('dragstart', (e) => {
        // Create a custom drag image (bookmark icon)
        const dragIcon = document.createElement('div');
        dragIcon.className = 'drag-icon';
        dragIcon.innerHTML = '<i class="fas fa-bookmark fa-2x"></i>';
        dragIcon.style.position = 'absolute';
        dragIcon.style.top = '-1000px';
        dragIcon.style.color = '#3273dc';
        document.body.appendChild(dragIcon);

        // Set the custom drag image
        e.dataTransfer.setDragImage(dragIcon, 25, 25);

        item.closest('.card').classList.add('dragging');
        e.dataTransfer.setData('text/plain', item.dataset.uid);

        // Remove the temporary element after a short delay
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
                "Content-type": "application/json"
            }
        });

        if (response.ok) {
            sessionStorage.setItem(toastSuccess, "Bookmark successfully moved!");
        } else {
            sessionStorage.setItem(toastError, error);
        }
        closeAllModals();
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
                "Content-type": "application/json"
            }
        });

        if (response.ok) {
            sessionStorage.setItem(toastSuccess, "Category successfully deleted!");
        } else {
            sessionStorage.setItem(toastError, error);
        }

        closeAllModals();
        window.location.href = "/dashboard";
    }
    closeModal($deleteCategoryModal);
});

document.getElementById('confirm-empty-trash').addEventListener('click', async () => {
    const response = await fetch("/api/v1/items/trash", {
        method: "DELETE",
        headers: {
            "Content-type": "application/json"
        }
    });

    if (response.ok) {
        sessionStorage.setItem(toastSuccess, "Trash successfully emptied!");
    } else {
        sessionStorage.setItem(toastError, error);
    }

    closeAllModals();
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
                "Content-type": "application/json"
            }
        });

        if (response.ok) {
            sessionStorage.setItem(toastSuccess, "Bookmark successfully deleted!");
        } else {
            sessionStorage.setItem(toastError, error);
        }

        closeAllModals();
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
                    "Content-type": "application/json"
                }
            });

            if (response.ok) {
                sessionStorage.setItem(toastSuccess, "Category successfully created!");
            } else {
                sessionStorage.setItem(toastError, error);
            }

            document.getElementById('category').value = '';
            closeAllModals();
            window.location.href = "/dashboard";
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

    // Trigger reflow to enable transition
    toast.offsetHeight;

    // Show toast
    requestAnimationFrame(() => {
        toast.classList.add('is-active');
    });

    // Remove toast after duration
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

        const url = document.getElementById('bookmark-url').value;
        const category = document.getElementById('bookmark-category').value;

        if (url && category) {
            // Show loading state on button
            $confirmAddBookmark.classList.add('is-loading');
            $confirmAddBookmark.disabled = true;

            // Make POST request to API
            const response = await fetch('/api/v1/items', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    url: url,
                    category: category.toLowerCase()
                })
            })

            if (response.ok) {
                sessionStorage.setItem(toastSuccess, "Bookmark successfully created!");
            } else {
                sessionStorage.setItem(toastError, error);
            }

            closeAllModals();
            window.location.href = "/dashboard/" + category;

            $addBookmarkSubmit.classList.remove('is-loading');
            $addBookmarkSubmit.disabled = false;
        }
    });
}

document.querySelectorAll('.image-container').forEach(container => {
    const img = container.querySelector('.image-with-fallback');
    const spinner = container.querySelector('.spinner');
    const timeout = 4000; // 4 seconds timeout
    let timeoutReached = false;

    // Function to hide the spinner and show the image
    const showImage = () => {
        spinner.style.display = 'none'; // Hide the spinner
        img.style.display = 'block';    // Show the image
    };

    // Function to handle the success case (image loaded successfully)
    const handleSuccess = () => {
        if (!timeoutReached) {
            timeoutReached = true;
            showImage();
        }
    };

    // Function to handle the error case (image failed or timeout)
    const handleError = () => {
        if (!timeoutReached) {
            timeoutReached = true;
            spinner.style.display = 'none'; // Hide the spinner
            img.style.display = 'block';    // Show the image
            img.src = '/assets/images/placeholder.svg';    // Set the fallback placeholder
        }
    };

    // Add event listener for image load success
    img.onload = handleSuccess;

    // Add error handling for failed image loading
    img.onerror = handleError;

    // Explicitly handle cached images
    if (img.complete) {
        // If the image is already complete (cached), check its natural width
        if (img.naturalWidth > 0) {
            // Image has loaded successfully (cached or just loaded)
            handleSuccess();
        } else {
            // Image failed to load (cached error or not found)
            handleError();
        }
    } else {
        // Set a timeout to handle fallback if the image takes too long
        setTimeout(() => {
            if (!timeoutReached) handleError();
        }, timeout);
    }
});