if (!requireAuth()) {
    throw new Error('Not authenticated');
}

renderNavbar('search');

const urlParams = new URLSearchParams(window.location.search);

// Prefill the filter bar from whatever the dashboard's quick-search sent over
document.getElementById('s-from').value = urlParams.get('from') || '';
document.getElementById('s-to').value = urlParams.get('to') || '';
document.getElementById('s-date').value = urlParams.get('date') || '';
if (urlParams.get('class')) document.getElementById('s-class').value = urlParams.get('class');

const CLASS_LABELS = { Sleeper: 'Sleeper', '3A': 'AC 3 Tier', '2A': 'AC 2 Tier', '1A': 'AC 1st Class' };

function resultCardHtml(t, journeyDate, journeyClass) {
    const cls = journeyClass || 'Sleeper';
    const bookParams = new URLSearchParams({
        trainId: t.id,
        from: t.source,
        to: t.destination,
        date: journeyDate || '',
        class: cls
    });
    return `
        <div class="train-card">
            <div class="train-card-head">
                <h3 style="color:#1e2430">${t.name}</h3>
                <span class="train-no-badge">${t.trainNo}</span>
            </div>
            <div class="route">${t.source} <span style="color:#1e2430">➔</span> ${t.destination}</div>
            <p class="meta">Duration: <strong>${t.duration}</strong></p>
            <p class="meta">Available Seats: <strong>${t.seats}</strong></p>
            <div class="price-row">
                <div class="price-tag">₹ ${t.fares[cls]} (${CLASS_LABELS[cls] || cls})</div>
            </div>
            <div class="price-row" style="margin-top:10px">
                <a class="book-btn" style="width:100%" href="booking.html?${bookParams.toString()}">Book Ticket</a>
            </div>
        </div>
    `;
}

async function runSearch() {
    const container = document.getElementById('results');
    const from = document.getElementById('s-from').value.trim().toLowerCase();
    const to = document.getElementById('s-to').value.trim().toLowerCase();
    const date = document.getElementById('s-date').value;
    const cls = document.getElementById('s-class').value;

    try {
        let trains = await apiGetTrains();

        // Simple client-side filtering; the linked list itself lives on the backend
        if (from) trains = trains.filter(t => t.source.toLowerCase().includes(from));
        if (to) trains = trains.filter(t => t.destination.toLowerCase().includes(to));

        if (trains.length === 0) {
            container.innerHTML = '<p class="loading">No trains found for this route.</p>';
            return;
        }

        container.innerHTML = trains.map(t => resultCardHtml(t, date, cls)).join('');
    } catch (err) {
        container.innerHTML = '<p class="loading" style="color:#ef4444;">Failed to connect to backend server. Make sure BackendServer is running on port 8080.</p>';
    }
}

document.getElementById('search-form').addEventListener('submit', (e) => {
    e.preventDefault();
    runSearch();
});

runSearch();
setupCityAutocomplete();