// api.js — fetch wrapper, no external libs

const csrfTokenElement = document.getElementById('x-csrf-token');
const csrfToken = csrfTokenElement?.dataset?.csrfToken ?? "";

const defaultHeaders = {
    'x-csrf-token': csrfToken,
    'Content-Type': 'application/json'
};

async function baseRequest(url, options = {}) {
    const response = await fetch(url, {
        headers: { ...defaultHeaders, ...(options.headers || {}) },
        ...options
    });

    // Throw on non-2xx (Axios/Ky-like)
    if (!response.ok) {
        const error = new Error(`HTTP ${response.status}`);
        error.response = response;
        throw error;
    }

    return response;
}

// Convenience helpers mirroring your old Axios usage
window.apiPost = (url, body, options = {}) =>
    baseRequest(url, {
        method: 'POST',
        body: body != null ? JSON.stringify(body) : undefined,
        ...options
    });

window.apiPut = (url, body, options = {}) =>
    baseRequest(url, {
        method: 'PUT',
        body: body != null ? JSON.stringify(body) : undefined,
        ...options
    });

window.apiDelete = (url, options = {}) =>
    baseRequest(url, {
        method: 'DELETE',
        ...options
    });

// For polling: allow 200 and 304 without throwing
window.apiPostNoThrow = (url, body, options = {}) =>
    fetch(url, {
        method: 'POST',
        headers: { ...defaultHeaders, ...(options.headers || {}) },
        body: body != null ? JSON.stringify(body) : undefined,
        ...options
    });
