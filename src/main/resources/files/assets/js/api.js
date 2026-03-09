const apiCsrfTokenElement = document.getElementById('x-csrf-token');
const apiCsrfToken = apiCsrfTokenElement?.dataset?.csrfToken ?? "";

const apiDefaultHeaders = {
    'x-csrf-token': apiCsrfToken,
    'Content-Type': 'application/json'
};

async function apiBaseRequest(url, options = {}) {
    const response = await fetch(url, {
        headers: { ...apiDefaultHeaders, ...(options.headers || {}) },
        ...options
    });

    if (!response.ok) {
        const error = new Error(`HTTP ${response.status}`);
        error.response = response;
        throw error;
    }

    return response;
}

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
        headers: { ...apiDefaultHeaders, ...(options.headers || {}) },
        body: body != null ? JSON.stringify(body) : undefined,
        ...options
    });
