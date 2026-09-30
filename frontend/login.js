// If already logged in, skip straight to the dashboard
if (getCurrentUser()) {
    window.location.href = 'index.html';
}

function showAuthError(message) {
    document.getElementById('auth-error').innerHTML = `<div class="auth-error">${message}</div>`;
}

document.getElementById('login-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;

    const result = loginUser(email, password);
    if (result.ok) {
        window.location.href = 'index.html';
    } else {
        showAuthError(result.message);
    }
});