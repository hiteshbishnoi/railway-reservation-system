// If already logged in, skip straight to the dashboard
if (getCurrentUser()) {
    window.location.href = 'index.html';
}

function showAuthError(message) {
    document.getElementById('auth-error').innerHTML = `<div class="auth-error">${message}</div>`;
}

document.getElementById('register-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const name = document.getElementById('name').value.trim();
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    const confirmPassword = document.getElementById('confirm-password').value;

    if (password !== confirmPassword) {
        showAuthError('Passwords do not match.');
        return;
    }

    const result = registerUser(name, email, password);
    if (result.ok) {
        // Auto-login right after registering
        loginUser(email, password);
        window.location.href = 'index.html';
    } else {
        showAuthError(result.message);
    }
});