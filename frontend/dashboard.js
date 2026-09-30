if (!requireAuth()) {
    // requireAuth() already redirected to login.html
    throw new Error('Not authenticated');
}

renderNavbar('dashboard');

function trainCardHtml(t) {
    return `
        <div class="train-card">
            <div class="train-card-head">
                <h3>🚂 ${t.name}</h3>
                <span class="train-no-badge">${t.trainNo}</span>
            </div>
            <div class="route">${t.source} ➔ ${t.destination}</div>
            <p class="meta">⭐ ${t.rating} | Seats: ${t.seats}</p>
            <div class="price-row">
                <div class="price-tag">From ₹${t.fares.Sleeper}</div>
                <a class="book-btn" href="booking.html?trainId=${t.id}">Book</a>
            </div>
        </div>
    `;
}

async function loadPopularTrains() {
    const container = document.getElementById('popular-trains');
    try {
        const trains = await apiGetTrains();
        if (trains.length === 0) {
            container.innerHTML = '<p class="loading">No trains currently scheduled.</p>';
            return;
        }
        container.innerHTML = trains.map(trainCardHtml).join('');
    } catch (err) {
        container.innerHTML = '<p class="loading" style="color:#ef4444;">Failed to connect to backend server. Make sure BackendServer is running on port 8080.</p>';
    }
}

document.getElementById('quick-search-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const from = document.getElementById('qs-from').value.trim();
    const to = document.getElementById('qs-to').value.trim();
    const date = document.getElementById('qs-date').value;
    const cls = document.getElementById('qs-class').value;

    const params = new URLSearchParams({ from, to, date, class: cls });
    window.location.href = `search.html?${params.toString()}`;
});

setupCityAutocomplete();