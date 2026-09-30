/*
 * Filed Papers — dashboard behaviour.
 *
 * The pages are rendered by the server; this file only covers what a page load
 * cannot do: dialogs, the command palette, selecting several bookmarks at once,
 * drag and drop, and the theme. Every mutation goes through the same /api/v1
 * endpoints the iOS app uses, followed by a reload so the sidebar counters and
 * the rendered list never drift apart.
 */
const $id = (id) => document.getElementById(id);
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

const on = (target, event, handler) => {
    const el = typeof target === 'string' ? $(target) : target;
    if (el) el.addEventListener(event, handler);
};

const onAll = (sel, event, handler) => $$(sel).forEach(el => el.addEventListener(event, handler));

const TOAST_SUCCESS = 'toast-success';
const TOAST_ERROR = 'toast-error';

const i18n = $id('i18n-js').dataset;

let categoryToRename = null;
let categoryToDelete = null;

/* ==========================================================================
   TOASTS
   ========================================================================== */
function showToast(message, type = 'success', duration = 4000) {
    const container = $id('toasts');
    if (!container || !message) return;

    const toast = document.createElement('div');
    toast.className = `toast${type === 'error' ? ' toast--rust' : ''}`;
    toast.innerHTML = `<svg aria-hidden="true"><use href="#i-${type === 'error' ? 'warn' : 'check-circle'}"/></svg><span></span>`;
    $('span', toast).textContent = message;

    container.appendChild(toast);
    toast.addEventListener('click', () => toast.remove());
    setTimeout(() => toast.remove(), duration);
}
window.showToast = showToast;

// A mutation redirects, so the confirmation has to survive the page load.
function toastAfterReload(message, type = 'success') {
    sessionStorage.setItem(type === 'error' ? TOAST_ERROR : TOAST_SUCCESS, message);
}

function flushStoredToasts() {
    const success = sessionStorage.getItem(TOAST_SUCCESS);
    if (success) {
        showToast(success);
        sessionStorage.removeItem(TOAST_SUCCESS);
    }

    const error = sessionStorage.getItem(TOAST_ERROR);
    if (error) {
        showToast(error, 'error');
        sessionStorage.removeItem(TOAST_ERROR);
    }
}

/*
 * A failed request used to be followed by a reload, which wiped both the toast
 * and the console line that said what went wrong. Now the page stays where it
 * is and the message from the server is what the toast shows.
 */
function fail(error) {
    const response = error?.response;

    if (!response) {
        console.error('Filed Papers:', error);
        showToast(i18n.error, 'error', 8000);
        return;
    }

    response.text()
        .then(body => {
            let message = '';
            try {
                message = JSON.parse(body)?.message ?? '';
            } catch (e) {
                message = body;
            }

            console.error(`Filed Papers: ${response.status} ${response.url}`, message || '(no body)');
            showToast(message ? `${i18n.error} (${message})` : i18n.error, 'error', 8000);
        })
        .catch(() => {
            console.error(`Filed Papers: ${response.status} ${response.url}`);
            showToast(i18n.error, 'error', 8000);
        });
}

/* ==========================================================================
   DIALOGS
   ========================================================================== */
function openDialog(el) {
    if (!el) return;
    closeDialogs();
    el.classList.add('is-open');

    const field = $('input:not([type=hidden]), select', el);
    if (field) setTimeout(() => field.focus(), 60);
}

function closeDialogs() {
    $$('.veil.is-open').forEach(el => el.classList.remove('is-open'));
}

function wireDialogs() {
    onAll('[data-close]', 'click', closeDialogs);
    onAll('.veil', 'click', (e) => {
        if (e.target.classList.contains('veil')) closeDialogs();
    });
}

/* ==========================================================================
   THEME
   ========================================================================== */
function currentTheme() {
    return document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light';
}

function applyTheme(theme) {
    document.documentElement.dataset.theme = theme;
    try { localStorage.setItem('theme', theme); } catch (e) { /* private mode */ }

    const use = $('#menu-theme use');
    if (use) use.setAttribute('href', theme === 'dark' ? '#i-sun' : '#i-moon');
}

function toggleTheme() {
    applyTheme(currentTheme() === 'dark' ? 'light' : 'dark');
}

/* ==========================================================================
   SHELL — account menu and the off-canvas sidebar
   ========================================================================== */
function wireShell() {
    const menu = $id('account-menu');
    const account = $id('account');

    on(account, 'click', (e) => {
        e.stopPropagation();
        const open = menu.classList.toggle('is-open');
        account.setAttribute('aria-expanded', String(open));
    });

    on(document, 'click', (e) => {
        if (menu && !menu.contains(e.target) && !account?.contains(e.target)) {
            menu.classList.remove('is-open');
            account?.setAttribute('aria-expanded', 'false');
        }

        const sidebar = $id('sidebar');
        const burger = $id('burger');
        if (document.body.classList.contains('nav-open')
            && !sidebar?.contains(e.target) && !burger?.contains(e.target)) {
            document.body.classList.remove('nav-open');
        }
    });

    on('#burger', 'click', (e) => {
        e.stopPropagation();
        document.body.classList.toggle('nav-open');
    });

    on('#menu-theme', 'click', toggleTheme);
    applyTheme(currentTheme());
}

/* ==========================================================================
   CATEGORIES
   ========================================================================== */
function addCategory() {
    const input = $id('category');
    const name = input?.value.trim();

    if (!name) {
        input?.classList.add('is-invalid');
        return;
    }

    window.apiPost('/api/v1/categories', { name })
        .then(() => {
            toastAfterReload(i18n.categoryCreatedSuccess);
            window.location.href = '/dashboard';
        })
        .catch(fail);
}

function renameCategory() {
    const input = $id('existing-category');
    const name = input?.value.trim();
    const uid = categoryToRename?.dataset.uid;

    if (!name || !uid) {
        input?.classList.add('is-invalid');
        return;
    }

    window.apiPut('/api/v1/categories', { uid, name })
        .then(() => {
            toastAfterReload(i18n.categoryRenamedSuccess);
            window.location.href = '/dashboard/' + uid;
        })
        .catch(fail);
}

function deleteCategory() {
    const uid = categoryToDelete?.dataset.uid;
    if (!uid) return;

    window.apiDelete(`/api/v1/categories/${uid}`)
        .then(() => {
            toastAfterReload(i18n.categoryDeletedSuccess);
            window.location.href = '/dashboard';
        })
        .catch(fail);
}

function emptyTrash() {
    window.apiDelete('/api/v1/items/trash')
        .then(() => {
            toastAfterReload(i18n.trashEmptiedSuccess);
            window.location.reload();
        })
        .catch(fail);
}

/* ==========================================================================
   BOOKMARKS
   ========================================================================== */
function addBookmark() {
    const url = $id('bookmark-url');
    const category = $id('bookmark-category');
    const button = $id('confirm-add-bookmark');

    if (!url?.value) {
        url?.classList.add('is-invalid');
        return;
    }

    button.classList.add('is-busy');
    window.apiPost('/api/v1/items?async=true', { url: url.value, category: category.value })
        .then(() => {
            toastAfterReload(i18n.bookmarkCreatedSuccess);
            window.location.href = '/dashboard/' + category.value;
        })
        .catch((error) => {
            button.classList.remove('is-busy');
            fail(error);
        });
}

function trashItem(item) {
    const uid = item.dataset.uid;
    item.style.transition = 'opacity .25s ease';
    item.style.opacity = '0';

    window.apiPut(`/api/v1/items/${uid}`, {})
        .then(() => {
            toastAfterReload(i18n.bookmarkDeletedSuccess);
            window.location.reload();
        })
        .catch((error) => {
            item.style.opacity = '';
            fail(error);
        });
}

function archiveItem(uid) {
    // Archiving fetches and stores the page, which takes a while - the server
    // answers immediately and works in the background, so there is no reload.
    window.apiPost(`/api/v1/archive/${uid}`, {})
        .then(() => showToast(i18n.archivedSuccess))
        .catch(() => showToast(i18n.error, 'error'));
}

/* ==========================================================================
   SELECTING SEVERAL BOOKMARKS
   A click opens a bookmark; selecting is a separate mode, entered on purpose.
   While it is on, the per-item actions are hidden and the tray is the only
   place an action comes from.
   ========================================================================== */
function isPicking() {
    return document.body.classList.contains('picking');
}

function pickedItems() {
    return $$('.item.is-picked');
}

function pickedUids() {
    return pickedItems().map(item => item.dataset.uid);
}

function refreshTray() {
    const count = pickedItems().length;
    const label = $id('tray-n');

    if (label) {
        label.textContent = count === 1
            ? i18n.selectedOne
            : i18n.selectedMany.replace('{0}', String(count));
    }

    if (!count) stopPicking();
}

function startPicking() {
    document.body.classList.add('picking');
    refreshTray();
}

function stopPicking() {
    document.body.classList.remove('picking');
    $$('.item.is-picked').forEach(item => {
        item.classList.remove('is-picked');
        $('.item__pick', item)?.setAttribute('aria-checked', 'false');
    });
}

function togglePick(item) {
    const box = $('.item__pick', item);
    const picked = !item.classList.contains('is-picked');

    item.classList.toggle('is-picked', picked);
    box?.setAttribute('aria-checked', String(picked));
    refreshTray();
}

function wirePicking() {
    on('#pick-start', 'click', () => {
        const first = $('.item');
        if (first) {
            document.body.classList.add('picking');
            togglePick(first);
        }
    });
    on('#pick-done', 'click', stopPicking);

    on(document, 'click', (e) => {
        const item = e.target.closest('.item');
        if (!item) return;

        // Modifier-click starts the mode straight from a tile
        if (!isPicking() && (e.metaKey || e.ctrlKey || e.shiftKey)) {
            e.preventDefault();
            document.body.classList.add('picking');
            togglePick(item);
            return;
        }

        if (isPicking() && !e.target.closest('.act')) {
            e.preventDefault();
            togglePick(item);
        }
    });

    on('#bulk-move', 'click', () => {
        const count = pickedItems().length;
        $id('bulk-move-count').textContent = i18n.selectedMany.replace('{0}', String(count));
        openDialog($id('bulk-move-modal'));
    });

    on('#confirm-bulk-move', 'click', () => {
        const uids = pickedUids();
        const category = $id('bulk-move-category')?.value;
        if (!uids.length || !category) return;

        window.apiPut('/api/v1/items/bulk/move', { uids, category })
            .then(() => {
                toastAfterReload(i18n.itemsMovedSuccess);
                window.location.reload();
            })
            .catch(fail);
    });

    on('#bulk-delete', 'click', () => {
        const count = pickedItems().length;
        $id('bulk-delete-count').textContent = i18n.selectedMany.replace('{0}', String(count));
        openDialog($id('bulk-delete-confirm-modal'));
    });

    on('#confirm-bulk-delete', 'click', () => {
        const uids = pickedUids();
        if (!uids.length) return;

        window.apiPut('/api/v1/items/bulk/delete', { uids })
            .then(() => {
                toastAfterReload(i18n.itemsDeletedSuccess);
                window.location.reload();
            })
            .catch(fail);
    });

    on('#bulk-copy', 'click', () => {
        const links = pickedItems()
            .map(item => $('.item__link', item)?.href)
            .filter(Boolean)
            .join('\n');

        navigator.clipboard?.writeText(links)
            .then(() => { showToast(i18n.itemsCopiedSuccess); stopPicking(); })
            .catch(() => showToast(i18n.error, 'error'));
    });
}

/* ==========================================================================
   DRAG & DROP — a tile onto a category in the sidebar
   ========================================================================== */
function wireDragAndDrop() {
    // A tile is a link, and dragging a link makes the browser put its url into
    // text/plain by itself. Reading that back as an item uid is what produced
    // "itemUid is null or invalid", so the uids travel in a type of their own
    // and a drop without it is not ours to handle.
    const MIME = 'application/x-filedpapers-items';

    // Where the drag started, so a drop onto that same category can be ignored
    let dragSource = null;

    on(document, 'dragstart', (e) => {
        const item = e.target.closest('.item');
        if (!item) return;

        const uids = isPicking() && item.classList.contains('is-picked') ? pickedUids() : [item.dataset.uid];

        dragSource = item.dataset.category;
        item.classList.add('is-lifting');
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.clearData();
        e.dataTransfer.setData(MIME, uids.join(','));

        const ghost = document.createElement('div');
        ghost.className = 'drag-ghost';
        ghost.innerHTML = '<svg aria-hidden="true"><use href="#i-bookmark"/></svg>';
        ghost.appendChild(document.createTextNode(uids.length > 1 ? String(uids.length) : ''));
        document.body.appendChild(ghost);
        e.dataTransfer.setDragImage(ghost, 20, 20);
        setTimeout(() => ghost.remove(), 0);
    });

    on(document, 'dragend', (e) => e.target.closest('.item')?.classList.remove('is-lifting'));

    const carriesItems = (e) => Array.from(e.dataTransfer.types || []).includes(MIME);

    onAll('#nav .navitem', 'dragover', (e) => {
        if (!carriesItems(e)) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        e.currentTarget.classList.add('is-drop');
    });

    onAll('#nav .navitem', 'dragleave', (e) => e.currentTarget.classList.remove('is-drop'));

    onAll('#nav .navitem', 'drop', (e) => {
        if (!carriesItems(e)) return;
        e.preventDefault();

        const target = e.currentTarget;
        target.classList.remove('is-drop');

        const uids = (e.dataTransfer.getData(MIME) || '').split(',').filter(Boolean);
        const category = target.dataset.uid;
        if (!uids.length || !category || category === dragSource) return;

        const request = uids.length === 1
            ? window.apiPut('/api/v1/items', { uid: uids[0], category })
            : window.apiPut('/api/v1/items/bulk/move', { uids, category });

        request
            .then(() => {
                toastAfterReload(uids.length === 1 ? i18n.bookmarkMovedSuccess : i18n.itemsMovedSuccess);
                window.location.reload();
            })
            .catch((error) => {
                $$('.item.is-lifting').forEach(el => el.classList.remove('is-lifting'));
                fail(error);
            });
    });
}

/* ==========================================================================
   COMMAND PALETTE — search across all categories, plus jumps and actions
   ========================================================================== */
const palette = {
    veil: null,
    input: null,
    list: null,
    timer: null,
    rows: []
};

function paletteOpen() {
    palette.veil = $id('veil-search');
    openDialog(palette.veil);
    palette.input.value = '';
    // Opening with the categories and actions already listed makes the palette
    // useful before a single key is pressed.
    paletteSearch();
    setTimeout(() => palette.input.focus(), 60);
}

function paletteRow(item) {
    const row = document.createElement('div');
    row.className = 'palette__row';
    row.dataset.href = item.href;
    if (item.external) row.dataset.external = 'true';
    if (item.action) row.dataset.action = item.action;

    row.innerHTML = `<svg aria-hidden="true"><use href="#i-${item.icon}"/></svg>`;
    row.appendChild(document.createTextNode(item.label));

    if (item.hint) {
        const hint = document.createElement('span');
        hint.className = 'palette__hint';
        hint.textContent = item.hint;
        row.appendChild(hint);
    }

    return row;
}

function renderPalette(groups) {
    palette.list.innerHTML = '';
    palette.rows = [];

    groups.forEach(group => {
        if (!group.items.length) return;

        const label = document.createElement('div');
        label.className = 'palette__label';
        label.textContent = group.label;
        palette.list.appendChild(label);

        group.items.forEach(item => {
            const row = paletteRow(item);
            palette.list.appendChild(row);
            palette.rows.push(row);
        });
    });

    if (!palette.rows.length) {
        const empty = document.createElement('div');
        empty.className = 'palette__row';
        empty.style.color = 'var(--ink-subtle)';
        empty.textContent = palette.input.value.trim() ? i18n.searchEmpty : i18n.searchHint;
        palette.list.appendChild(empty);
        return;
    }

    moveCursor(0);
}

function moveCursor(index) {
    palette.rows.forEach(row => row.classList.remove('is-cursor'));
    const row = palette.rows[Math.max(0, Math.min(index, palette.rows.length - 1))];
    if (row) {
        row.classList.add('is-cursor');
        row.scrollIntoView({ block: 'nearest' });
    }
}

function cursorIndex() {
    return palette.rows.findIndex(row => row.classList.contains('is-cursor'));
}

function localMatches(term) {
    const categories = $$('#nav .navitem')
        .filter(link => $('.navitem__t', link).textContent.toLowerCase().includes(term))
        .slice(0, 5)
        .map(link => ({
            icon: 'folder',
            label: $('.navitem__t', link).textContent.trim(),
            hint: $('.navitem__n', link)?.textContent.trim(),
            href: link.getAttribute('href')
        }));

    const actions = [
        { icon: 'plus', label: i18n.searchActionAdd, action: 'add-bookmark' },
        { icon: 'folder-plus', label: i18n.searchActionCategory, action: 'add-category' },
        { icon: 'moon', label: i18n.searchActionTheme, action: 'theme' }
    ].filter(action => !term || action.label.toLowerCase().includes(term));

    return { categories, actions };
}

function paletteSearch() {
    const term = palette.input.value.trim();
    const local = localMatches(term.toLowerCase());

    if (term.length < 2) {
        renderPalette([
            { label: i18n.searchCategories, items: local.categories },
            { label: i18n.searchActions, items: local.actions }
        ]);
        return;
    }

    window.apiGet('/api/v1/search?q=' + encodeURIComponent(term))
        .then(response => response.json())
        .then(data => {
            const bookmarks = (data.items || []).map(item => ({
                icon: 'bookmark',
                label: item.title,
                hint: item.domain || item.category,
                href: item.url,
                external: true
            }));

            renderPalette([
                { label: i18n.searchBookmarks, items: bookmarks },
                { label: i18n.searchCategories, items: local.categories },
                { label: i18n.searchActions, items: local.actions }
            ]);
        })
        .catch(() => renderPalette([
            { label: i18n.searchCategories, items: local.categories },
            { label: i18n.searchActions, items: local.actions }
        ]));
}

function runPaletteRow(row) {
    if (!row) return;

    if (row.dataset.action === 'add-bookmark') {
        openDialog($id('add-bookmark-modal'));
        return;
    }
    if (row.dataset.action === 'add-category') {
        openDialog($id('add-category-modal'));
        return;
    }
    if (row.dataset.action === 'theme') {
        closeDialogs();
        toggleTheme();
        return;
    }
    if (row.dataset.href) {
        if (row.dataset.external === 'true') {
            window.open(row.dataset.href, '_blank', 'noopener');
            closeDialogs();
        } else {
            window.location.href = row.dataset.href;
        }
    }
}

function wirePalette() {
    palette.veil = $id('veil-search');
    palette.input = $id('search-input');
    palette.list = $id('search-results');
    if (!palette.veil) return;

    on('#open-search', 'click', paletteOpen);
    on('#open-search-sm', 'click', paletteOpen);
    on('#menu-search', 'click', paletteOpen);

    on(palette.input, 'input', () => {
        clearTimeout(palette.timer);
        palette.timer = setTimeout(paletteSearch, 180);
    });

    on(palette.input, 'keydown', (e) => {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            moveCursor(cursorIndex() + 1);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            moveCursor(cursorIndex() - 1);
        } else if (e.key === 'Enter') {
            e.preventDefault();
            runPaletteRow(palette.rows[cursorIndex()]);
        }
    });

    on(palette.list, 'click', (e) => {
        const row = e.target.closest('.palette__row');
        if (row) runPaletteRow(row);
    });
}

/* ==========================================================================
   VIEW MODE — the same markup, laid out as a grid or as rows
   ========================================================================== */
const VIEW_KEY = 'fp-view';

function applyView(view) {
    const shelf = $id('shelf');
    if (!shelf) return;

    shelf.classList.toggle('shelf--list', view === 'list');
    $id('view-list')?.classList.toggle('is-on', view === 'list');
    $id('view-grid')?.classList.toggle('is-on', view !== 'list');
    try { localStorage.setItem(VIEW_KEY, view); } catch (e) { /* private mode */ }
}

function wireView() {
    let stored = 'grid';
    try { stored = localStorage.getItem(VIEW_KEY) || 'grid'; } catch (e) { /* private mode */ }

    applyView(stored);
    on('#view-list', 'click', () => applyView('list'));
    on('#view-grid', 'click', () => applyView('grid'));
}

/* ==========================================================================
   FORMS
   ========================================================================== */
function wireForms() {
    // Imports and profile changes are plain form posts; the button says so.
    onAll('form[data-busy]', 'submit', (e) => {
        const button = $id(e.currentTarget.dataset.busy);
        if (button) {
            button.classList.add('is-busy');
            button.disabled = true;
        }
    });

    on('#importfile', 'change', (e) => {
        const name = e.target.files?.[0]?.name;
        if (name) $id('importfile-name').textContent = name;
    });

    on('#bookmark-url', 'input', () => $id('bookmark-url').classList.remove('is-invalid'));
    on('#category', 'input', () => $id('category').classList.remove('is-invalid'));

    // One digit per box, moving on by itself
    const otp = $$('.otp-input');
    otp.forEach((input, index) => {
        input.addEventListener('input', (e) => {
            e.target.value = e.target.value.replace(/[^0-9]/g, '').slice(0, 1);
            if (e.target.value && index < otp.length - 1) otp[index + 1].focus();
        });
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Backspace' && !e.target.value && index > 0) otp[index - 1].focus();
        });
    });
    otp.find(input => input.offsetParent !== null)?.focus();
}

/* ==========================================================================
   KEYBOARD
   ========================================================================== */
function wireKeyboard() {
    on(document, 'keydown', (e) => {
        if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
            e.preventDefault();
            paletteOpen();
            return;
        }

        if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'j') {
            e.preventDefault();
            toggleTheme();
            return;
        }

        if (e.key === 'Escape') {
            closeDialogs();
            stopPicking();
            document.body.classList.remove('nav-open');
            return;
        }

        if (e.key === 'Enter') {
            const dialog = $('.veil.is-open .dialog');
            const confirm = dialog ? $('[data-confirm]', dialog) : null;
            if (confirm) {
                e.preventDefault();
                confirm.click();
            }
        }
    });
}

/* ==========================================================================
   LIVE UPDATES
   A bookmark can arrive from the ios app or the browser extension while this
   page sits open. The server pushes those over an event stream; the stream
   itself runs on a route without filters, so it authenticates with a single
   use ticket fetched from an endpoint that does have them.
   ========================================================================== */
const stream = {
    source: null,
    retry: null,
    attempts: 0,
    connecting: false,
    closing: false
};

const MAX_STREAM_ATTEMPTS = 6;

function currentCategoryUid() {
    return $('#nav .navitem.is-active')?.dataset.uid ?? null;
}

// Reloading under an open dialog or an active selection throws away what the
// user is in the middle of, so it waits for them to finish.
function reloadWhenIdle() {
    if ($('.veil.is-open') || isPicking()) {
        setTimeout(reloadWhenIdle, 2000);
        return;
    }

    window.location.reload();
}

function adjustCategoryCount(categoryUid, delta) {
    const counter = $(`#nav .navitem[data-uid="${categoryUid}"] .navitem__n`);
    if (!counter) return;

    const count = parseInt(counter.textContent, 10);
    if (!Number.isNaN(count)) counter.textContent = String(Math.max(0, count + delta));
}

function setCategoryCount(categoryUid, value) {
    const counter = $(`#nav .navitem[data-uid="${categoryUid}"] .navitem__n`);
    if (counter) counter.textContent = String(value);
}

/*
 * Puts a bookmark that arrived elsewhere at the top of the list, without
 * rebuilding the page. The markup comes from the server - the same macro the
 * list is rendered with - so there is no second copy of the tile in here.
 */
function insertTile(uid, countsTowardsCategory = true) {
    const shelf = $id('shelf');

    // An empty category shows a placeholder instead of a grid, and a tile has
    // nowhere to go in it. The same goes for a tile that is somehow here
    // already, which a reconnect can cause.
    if (!shelf || shelf.querySelector(`.item[data-uid="${uid}"]`)) {
        if (!shelf) reloadWhenIdle();
        return;
    }

    fetch(`/dashboard/item/${encodeURIComponent(uid)}`, { headers: { 'Accept': 'text/html' } })
        .then(response => response.ok ? response.text() : Promise.reject(new Error('HTTP ' + response.status)))
        .then(html => {
            shelf.insertAdjacentHTML('afterbegin', html.trim());
            updateItemCount();
            if (countsTowardsCategory) adjustCategoryCount(currentCategoryUid(), 1);
        })
        .catch(() => reloadWhenIdle());
}

// Keeps the line under the page title honest after a tile was inserted
function updateItemCount() {
    const counter = $('.page-head__meta span');
    const shelf = $id('shelf');
    if (!counter || !shelf) return;

    const count = shelf.querySelectorAll('.item').length;
    counter.textContent = count + ' ' + (count === 1 ? i18n.bookmark : i18n.bookmarks);
}

/*
 * Bookmarks changed category somewhere else: drop the tiles that are on this
 * page, pull in the ones that now belong here, and correct both counters.
 */
function onItemsMoved(payload) {
    const uids = Array.isArray(payload.uids) ? payload.uids : [];
    const current = currentCategoryUid();
    const shelf = $id('shelf');

    let removed = 0;
    uids.forEach(uid => {
        const tile = shelf?.querySelector(`.item[data-uid="${uid}"]`);
        if (tile) {
            tile.remove();
            removed += 1;
        }
    });

    if (payload.from) adjustCategoryCount(payload.from, -uids.length);
    adjustCategoryCount(payload.to, uids.length);

    // The last tile is gone, so the page has to show the placeholder instead
    if (shelf && removed > 0 && !shelf.querySelector('.item')) {
        reloadWhenIdle();
        return;
    }

    if (removed > 0) updateItemCount();

    // They landed in the view that is open, so they have to show up here
    if (payload.to === current) {
        uids.forEach(uid => insertTile(uid, false));
    }
}

function onStreamMessage(event) {
    let payload;
    try {
        payload = JSON.parse(event.data);
    } catch (e) {
        return;
    }

    // The stream carries its own housekeeping: an acknowledgement on connect and
    // a keep alive every few seconds. Neither can be an sse comment, so both
    // arrive as events and are dropped here.
    if (typeof payload.event === 'string' && payload.event.startsWith('stream.')) return;

    if (payload.event === 'items.moved') {
        onItemsMoved(payload);
        return;
    }

    if (payload.event === 'trash.emptied') {
        // Everything in that view is gone, and an empty category shows a
        // placeholder instead of a grid - that is a different page.
        if (payload.categoryUid === currentCategoryUid()) {
            reloadWhenIdle();
        } else {
            setCategoryCount(payload.categoryUid, 0);
        }
        return;
    }

    if (payload.event === 'item.added') {
        // Only the view the bookmark belongs to changes; for the others the
        // sidebar counter is the whole of it.
        if (payload.categoryUid === currentCategoryUid()) {
            insertTile(payload.uid);
        } else {
            adjustCategoryCount(payload.categoryUid, 1);
        }
    }
}

// Only the pages that show bookmarks have anything to update
function streamWanted() {
    return !!($id('shelf') || $('#nav .navitem'));
}

function connectStream() {
    if (stream.closing || stream.connecting || stream.source) return;
    if (!streamWanted() || stream.attempts >= MAX_STREAM_ATTEMPTS) return;

    stream.connecting = true;
    window.apiPost('/api/v1/events/ticket', {})
        .then(response => response.json())
        .then(data => {
            stream.connecting = false;
            if (stream.closing) return;
            if (!data.ticket) throw new Error('no ticket');

            const source = new EventSource('/api/v1/events?ticket=' + encodeURIComponent(data.ticket));
            stream.source = source;

            source.addEventListener('open', () => { stream.attempts = 0; });
            source.addEventListener('message', onStreamMessage);

            // A ticket is spent once it has been used, so the reconnect that
            // EventSource does on its own would be rejected - close it and come
            // back with a new ticket instead.
            source.addEventListener('error', () => {
                source.close();
                stream.source = null;
                retryStream();
            });
        })
        .catch(() => {
            stream.connecting = false;
            retryStream();
        });
}

function retryStream() {
    if (stream.closing) return;

    stream.attempts += 1;
    if (stream.attempts >= MAX_STREAM_ATTEMPTS) {
        console.warn('Filed Papers: giving up on the event stream after ' + stream.attempts + ' attempts');
        return;
    }

    stream.retry = setTimeout(() => {
        stream.retry = null;
        connectStream();
    }, Math.min(2000 * 2 ** (stream.attempts - 1), 30000));
}

/*
 * Comes back to a stream that is gone. Two cases end up here: a page restored
 * from the back forward cache, which was closed on the way out, and a machine
 * that was asleep or offline long enough to burn through every retry. Both
 * leave the page alive but deaf, and neither recovers on its own.
 */
function rearmStream() {
    if (document.visibilityState === 'hidden' || navigator.onLine === false) return;

    stream.closing = false;
    if (stream.source || stream.connecting) return;

    // A pending retry is on a backoff of up to half a minute, and whatever woke
    // us up is a better reason to try again than waiting that out.
    if (stream.retry) {
        clearTimeout(stream.retry);
        stream.retry = null;
    }

    stream.attempts = 0;
    connectStream();
}

// Leaving the page tears the connection down anyway; saying so avoids a last
// reconnect attempt while the document is already going away.
window.addEventListener('pagehide', () => {
    stream.closing = true;
    stream.source?.close();
    stream.source = null;
});

window.addEventListener('pageshow', rearmStream);
window.addEventListener('online', rearmStream);
document.addEventListener('visibilitychange', rearmStream);

/* ==========================================================================
   WIRING
   ========================================================================== */
wireDialogs();
wireShell();
wirePicking();
wireDragAndDrop();
wirePalette();
wireView();
wireForms();
wireKeyboard();

on('#add-bookmark', 'click', () => openDialog($id('add-bookmark-modal')));
on('#add-bookmark-sm', 'click', () => openDialog($id('add-bookmark-modal')));
on('#add-bookmark-empty', 'click', () => openDialog($id('add-bookmark-modal')));
on('#add-category-button', 'click', () => openDialog($id('add-category-modal')));
on('#add-category-submit', 'click', addCategory);
on('#rename-category-submit', 'click', renameCategory);
on('#confirm-category-delete', 'click', deleteCategory);
on('#confirm-empty-trash', 'click', emptyTrash);
on('#confirm-add-bookmark', 'click', addBookmark);
on('#logout-devices', 'click', () => openDialog($id('logout-devices-confirm-modal')));
on('#delete-account', 'click', () => openDialog($id('delete-account-modal')));

on('#confirm-logout-devices', 'click', () => {
    window.apiPost('/dashboard/profile/logout-devices', {})
        .then(() => {
            toastAfterReload(i18n.logoutDevicesSuccess);
            window.location.href = '/dashboard/profile';
        })
        .catch(fail);
});

onAll('.category-rename', 'click', (e) => {
    categoryToRename = e.currentTarget;
    $id('existing-category').value = categoryToRename.dataset.name;
    openDialog($id('rename-category-modal'));
});

onAll('.category-trash', 'click', (e) => {
    categoryToDelete = e.currentTarget;
    openDialog($id('delete-category-confirm-modal'));
});

onAll('.empty-trash', 'click', () => openDialog($id('empty-trash-confirm-modal')));

// Delegated, not bound per tile: a tile that arrives over the event stream is
// inserted into the page afterwards and has to work the same way.
on(document, 'click', (e) => {
    const trash = e.target.closest('.item-trash');
    if (trash) {
        e.preventDefault();
        e.stopPropagation();
        trashItem(trash.closest('.item'));
        return;
    }

    const archive = e.target.closest('.item-archive');
    if (archive) {
        e.preventDefault();
        e.stopPropagation();
        archiveItem(archive.dataset.uid);
    }
});

on(window, 'load', flushStoredToasts);

connectStream();
