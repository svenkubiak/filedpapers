const apiCsrfTokenElement = document.getElementById('x-csrf-token');
let apiCsrfToken = apiCsrfTokenElement?.dataset?.csrfToken ?? '';

// The session cookie that carries this token expires on its own (one hour by default), while a
// remembered login lasts much longer. Renew before it lapses so an open page keeps working.
const SESSION_REFRESH_MS = 20 * 60 * 1000;
const SESSION_REFRESH_GAP_MS = 30 * 1000;
let csrfRefreshedAt = Date.now();
let refreshInFlight = null;
let leavingForLogin = false;

function apiHeaders(extra) {
    return {
        'x-csrf-token': apiCsrfToken,
        'Content-Type': 'application/json',
        ...(extra || {})
    };
}

function applyCsrfToken(token) {
    if (!token) return;
    apiCsrfToken = token;
    csrfRefreshedAt = Date.now();
    if (apiCsrfTokenElement) apiCsrfTokenElement.dataset.csrfToken = token;
    document.querySelectorAll('input[name="x-csrf-token"]').forEach(input => {
        input.value = token;
    });
}

function sessionExpiredError() {
    const error = new Error('session expired');
    error.sessionExpired = true;
    if (!leavingForLogin) {
        leavingForLogin = true;
        window.location.assign('/auth/login');
    }
    return error;
}

// Reissues the session cookie. The token stays the same while the cookie is still valid.
function refreshCsrfToken() {
    if (refreshInFlight) return refreshInFlight;

    refreshInFlight = fetch('/dashboard/session', {
        method: 'GET',
        headers: { Accept: 'application/json' },
        cache: 'no-store',
        redirect: 'manual'
    }).then(async (response) => {
        if (response.type === 'opaqueredirect' || response.status === 401 || response.status === 403) {
            throw sessionExpiredError();
        }
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const type = response.headers.get('content-type') || '';
        if (!type.includes('json')) throw sessionExpiredError();

        const token = (await response.json())?.csrfToken;
        if (!token) throw new Error('missing csrf token');
        applyCsrfToken(token);
        return token;
    }).finally(() => {
        refreshInFlight = null;
    });

    return refreshInFlight;
}
window.refreshCsrfToken = refreshCsrfToken;

function refreshCsrfTokenIfDue() {
    if (Date.now() - csrfRefreshedAt < SESSION_REFRESH_GAP_MS) return;
    refreshCsrfToken().catch(() => {});
}

async function apiBaseRequest(url, options = {}, retry = true) {
    const response = await fetch(url, {
        ...options,
        headers: apiHeaders(options.headers)
    });

    if (response.status === 401 && retry) {
        try {
            await refreshCsrfToken();
        } catch (error) {
            if (error?.sessionExpired) throw error;
            const httpError = new Error(`HTTP ${response.status}`);
            httpError.response = response;
            throw httpError;
        }
        return apiBaseRequest(url, options, false);
    }

    if (!response.ok) {
        const error = new Error(`HTTP ${response.status}`);
        error.response = response;
        throw error;
    }

    return response;
}

// The API accepts the session cookie only with the CSRF token, so reads go through here too.
window.apiGet = (url, options = {}) =>
    apiBaseRequest(url, {
        method: 'GET',
        ...options
    });

window.apiPost = (url, body, options = {}) =>
    apiBaseRequest(url, {
        method: 'POST',
        body: body != null ? JSON.stringify(body) : undefined,
        ...options
    });

window.apiPut = (url, body, options = {}) =>
    apiBaseRequest(url, {
        method: 'PUT',
        body: body != null ? JSON.stringify(body) : undefined,
        ...options
    });

window.apiDelete = (url, options = {}) =>
    apiBaseRequest(url, {
        method: 'DELETE',
        ...options
    });

window.apiPostNoThrow = (url, body, options = {}) =>
    fetch(url, {
        method: 'POST',
        ...options,
        headers: apiHeaders(options.headers),
        body: body != null ? JSON.stringify(body) : undefined
    });

function submitWithFreshCsrf(form) {
    form.dataset.csrfReady = '1';
    if (typeof form.requestSubmit === 'function') form.requestSubmit();
    else form.submit();
}

document.querySelectorAll('form').forEach(form => {
    if (!form.querySelector('input[name="x-csrf-token"]')) return;

    form.addEventListener('submit', (event) => {
        if (form.dataset.csrfReady === '1') {
            delete form.dataset.csrfReady;
            return;
        }

        event.preventDefault();
        event.stopImmediatePropagation();
        refreshCsrfToken()
            .then(() => submitWithFreshCsrf(form))
            .catch((error) => {
                if (error?.sessionExpired) return;
                submitWithFreshCsrf(form);
            });
    });
});

setInterval(() => {
    if (document.visibilityState === 'hidden') return;
    refreshCsrfToken().catch(() => {});
}, SESSION_REFRESH_MS);

document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') refreshCsrfTokenIfDue();
});

let sawPageShow = false;
window.addEventListener('pageshow', () => {
    if (!sawPageShow) {
        sawPageShow = true;
        return;
    }
    refreshCsrfToken().catch(() => {});
});
