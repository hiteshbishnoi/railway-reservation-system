/* ==========================================================
   Simple client-side authentication (no backend auth endpoint).
   Accounts persist in localStorage; the active session lives in
   sessionStorage so it clears when the browser tab is closed.
   ========================================================== */

const USERS_KEY = 'tb_users';
const SESSION_KEY = 'tb_session';

function getUsers() {
    try {
        return JSON.parse(localStorage.getItem(USERS_KEY)) || [];
    } catch (e) {
        return [];
    }
}

function saveUsers(users) {
    localStorage.setItem(USERS_KEY, JSON.stringify(users));
}

// Returns { ok: true } or { ok: false, message }
function registerUser(name, email, password) {
    if (!name || !email || !password) {
        return { ok: false, message: 'Please fill in all fields.' };
    }
    const users = getUsers();
    if (users.find(u => u.email.toLowerCase() === email.toLowerCase())) {
        return { ok: false, message: 'An account with this email already exists.' };
    }
    users.push({ name, email, password });
    saveUsers(users);
    return { ok: true };
}

// Returns { ok: true } or { ok: false, message }
function loginUser(email, password) {
    const users = getUsers();
    const user = users.find(u => u.email.toLowerCase() === email.toLowerCase() && u.password === password);
    if (!user) {
        return { ok: false, message: 'Invalid email or password.' };
    }
    sessionStorage.setItem(SESSION_KEY, JSON.stringify({ name: user.name, email: user.email }));
    return { ok: true };
}

function getCurrentUser() {
    try {
        return JSON.parse(sessionStorage.getItem(SESSION_KEY));
    } catch (e) {
        return null;
    }
}

// Call at the top of every protected page. Redirects to login if no session.
function requireAuth() {
    const user = getCurrentUser();
    if (!user) {
        window.location.href = 'login.html';
        return null;
    }
    return user;
}

function logout() {
    sessionStorage.removeItem(SESSION_KEY);
    window.location.href = 'login.html';
}