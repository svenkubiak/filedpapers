    // Functions to open and close a modal
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

    // Add click event on plus icon
    const $addButton = document.getElementById('add-category-button');
    const $modal = document.getElementById('add-category-modal');
    const $deleteModal = document.getElementById('delete-confirm-modal');
    const $deleteCategoryModal = document.getElementById('delete-category-confirm-modal');
    const $emptyTrashModal = document.getElementById('empty-trash-confirm-modal');

    $addButton.addEventListener('click', () => {
        openModal($modal);
    });

    // Add click events to close the modal
    const $closeButtons = document.querySelectorAll('.modal-background, .modal-card-head .delete, .modal-card-foot .button:not(.is-danger)');
    $closeButtons.forEach(($close) => {
        $close.addEventListener('click', () => {
            closeAllModals();
        });
    });

    // Add keyboard event to close modal with escape key
    document.addEventListener('keydown', (event) => {
        if (event.code === 'Escape') {
            closeAllModals();
        }
    });

    // Get all draggable elements and category targets
    const draggableItems = document.querySelectorAll('.card-title[draggable="true"]');
    const categoryTargets = document.querySelectorAll('.menu-list a[data-category]');
    let $cardToDelete = null;

    // Add drag events to draggable elements
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

    // Add drop events to categories
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
                sessionStorage.setItem('toaster', "Bookmark succesfully moved!");
                closeAllModals();
                window.location.href = "/dashboard";
            }
        });
    });

    // Add click events to all trash icons
    document.querySelectorAll('.card-trash').forEach(trashIcon => {
        trashIcon.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            $cardToDelete = e.currentTarget.closest('.card');
            openModal($deleteModal);
        });
    });

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
                sessionStorage.setItem('toaster', "Category succesfully deleted!");
                closeAllModals();
                window.location.href = "/dashboard";
            }
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
            sessionStorage.setItem('toaster', "Trash succesfully emptied!");
            closeAllModals();
            window.location.href = "/dashboard";
        }
    });

    // Handle item delete confirmation
    document.getElementById('confirm-delete').addEventListener('click', async () => {
        if ($cardToDelete) {
            $cardToDelete.style.transition = 'all 0.3s ease';
            $cardToDelete.style.opacity = '0';
            setTimeout(() => {
                $cardToDelete.closest('.column').remove();
            }, 300);

            const uid = $cardToDelete.dataset.uid;
            const response = await fetch("/api/v1/items/" + uid, {
                method: "PUT",
                headers: {
                    "Content-type": "application/json"
                }
            });

            if (response.ok) {
                sessionStorage.setItem('toaster', "Bookmark succesfully deleted!");
                closeAllModals();
                window.location.href = "/dashboard";
            }
        }
    });

    // Handle Add category submission
    const $addBookmarkSubmit = document.getElementById('add-category-submit');
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
                    sessionStorage.setItem('toaster', "Category succesfully created!");
                    document.getElementById('category').value = '';
                    closeAllModals();
                    window.location.href = "/dashboard";
                }
            } else {
                // Show error if URL is empty
                document.getElementById('category').classList.add('is-danger');
            }
        });
    }

    // Remove error state when typing in URL field
    const $bookmarkUrl = document.getElementById('bookmark-url');
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

function showLoading() {
    const button = document.getElementById('loading-button');
    if (button) {
        button.classList.add('is-loading');
        button.disabled = true;
    }

    const button2 = document.getElementById('loading-button2');
    if (button2) {
        button2.classList.add('is-loading');
        button2.disabled = true;
    }
}

window.addEventListener('load', () => {
    const state = sessionStorage.getItem('toaster');
    if (state) {
        showToast(state);
        sessionStorage.setItem('toaster', "");
    }
});