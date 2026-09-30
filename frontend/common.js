const API_URL = "http://localhost:8080/api";

// Renders the shared navbar into every page and highlights the active link.
// Reads the logged-in user's name from the session (set by auth.js at login).
function renderNavbar(activePage) {
    const pages = [
        { key: 'dashboard', label: 'Home', href: 'index.html' },
        { key: 'search', label: 'Search', href: 'search.html' },
        { key: 'booking', label: 'Booking', href: 'booking.html' },
        { key: 'history', label: 'History', href: 'history.html' },
    ];

    const links = pages.map(p =>
        `<a href="${p.href}" class="${p.key === activePage ? 'active' : ''}">${p.label}</a>`
    ).join('');

    const user = typeof getCurrentUser === 'function' ? getCurrentUser() : null;
    const displayName = user ? user.name : 'Guest';
    const initial = displayName.trim().charAt(0).toUpperCase() || '?';

    document.getElementById('navbar-root').innerHTML = `
        <nav class="navbar">
            <div class="brand">🚉 Train Booking</div>
            <div class="nav-links">${links}</div>
            <div class="nav-right">
                <div class="avatar-pill">
                    <div class="avatar-circle">${initial}</div>
                    <span class="user-name">${displayName}</span>
                </div>
                <button class="logout-btn" onclick="logout()">Logout</button>
            </div>
        </nav>
    `;
}

// Small helper so every page can fetch trains without repeating error handling.
async function apiGetTrains() {
    const res = await fetch(`${API_URL}/trains`);
    if (!res.ok) throw new Error('Failed to load trains');
    return res.json();
}

async function apiGetHistory(pnr) {
    const url = pnr ? `${API_URL}/history?pnr=${encodeURIComponent(pnr)}` : `${API_URL}/history`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('Failed to load history');
    return res.json();
}

async function apiBook(payload) {
    const res = await fetch(`${API_URL}/book`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    return res.json();
}