document.addEventListener('DOMContentLoaded', () => {
    // Functions to open and close a modal
    function openModal($el) {
        $el.classList.add('is-active');
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
    const $addButton = document.getElementById('add-bookmark-button');
    const $modal = document.getElementById('add-bookmark-modal');
    const $deleteModal = document.getElementById('delete-confirm-modal');

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
            e.dataTransfer.setData('text/plain', item.dataset.id);

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

        target.addEventListener('drop', (e) => {
            e.preventDefault();
            target.classList.remove('drag-over');

            const cardId = e.dataTransfer.getData('text/plain');
            const category = target.dataset.category;

            // Here you would typically update your backend
            console.log(`Moving card ${cardId} to ${category}`);

            // Optional: Animate the card away
            const card = document.querySelector(`[data-id="${cardId}"]`).closest('.card');
            if (card) {
                card.style.transition = 'all 0.3s ease';
                card.style.opacity = '0';
                setTimeout(() => {
                    card.closest('.column').remove();
                }, 300);
            }

            // Update the counter
            const counter = target.querySelector('.tag');
            if (counter) {
                const currentCount = parseInt(counter.textContent);
                counter.textContent = currentCount + 1;
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

    // Handle delete confirmation
    document.getElementById('confirm-delete').addEventListener('click', () => {
        if ($cardToDelete) {
            $cardToDelete.style.transition = 'all 0.3s ease';
            $cardToDelete.style.opacity = '0';
            setTimeout(() => {
                $cardToDelete.closest('.column').remove();
            }, 300);

            // Update trash counter
            const trashCounter = document.querySelector('a[data-category="trash"] .tag');
            if (trashCounter) {
                const currentCount = parseInt(trashCounter.textContent);
                trashCounter.textContent = currentCount + 1;
            }
        }
        closeModal($deleteModal);
    });
});