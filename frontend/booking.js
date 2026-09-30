if (!requireAuth()) {
    throw new Error('Not authenticated');
}

renderNavbar('booking');

const params = new URLSearchParams(window.location.search);
const trainId = params.get('trainId');
const journeyDate = params.get('date') || '';
const journeyClass = params.get('class') || 'Sleeper';

let selectedTrain = null;

function setText(id, value) {
    document.getElementById(id).textContent = value;
}

function showBanner(type, message) {
    const root = document.getElementById('msg-banner-root');
    root.innerHTML = `<div class="msg-banner msg-${type}">${message}</div>`;
}

function updateFare() {
    const count = parseInt(document.getElementById('p-count').value || '1', 10);
    setText('sum-count', count);
    if (selectedTrain) {
        const fare = selectedTrain.fares[journeyClass] ?? selectedTrain.fares.Sleeper;
        setText('sum-fare', fare * count);
    }
}

async function loadTrain() {
    if (!trainId) {
        showBanner('error', 'No train selected. Please search and pick a train first.');
        document.getElementById('confirm-btn').disabled = true;
        return;
    }
    try {
        const trains = await apiGetTrains();
        selectedTrain = trains.find(t => String(t.id) === String(trainId));
        if (!selectedTrain) {
            showBanner('error', 'Selected train could not be found. It may no longer be scheduled.');
            document.getElementById('confirm-btn').disabled = true;
            return;
        }
        setText('sum-train', selectedTrain.name);
        setText('sum-trainno', selectedTrain.trainNo);
        setText('sum-from', params.get('from') || selectedTrain.source);
        setText('sum-to', params.get('to') || selectedTrain.destination);
        setText('sum-date', journeyDate || '-');
        setText('sum-class', journeyClass);
        setText('sum-duration', selectedTrain.duration);
        setText('sum-seats', selectedTrain.seats);
        updateFare();
    } catch (err) {
        showBanner('error', 'Failed to connect to backend server. Make sure BackendServer is running on port 8080.');
        document.getElementById('confirm-btn').disabled = true;
    }
}

// Live-update the right-hand summary as the passenger fills the form
document.getElementById('p-name').addEventListener('input', e => setText('sum-passenger', e.target.value || '-'));
document.getElementById('p-age').addEventListener('input', e => setText('sum-age', e.target.value || '-'));
document.getElementById('p-gender').addEventListener('change', e => setText('sum-gender', e.target.value));
document.getElementById('p-mobile').addEventListener('input', e => setText('sum-mobile', e.target.value || '-'));
document.getElementById('p-email').addEventListener('input', e => setText('sum-email', e.target.value || '-'));
document.getElementById('p-count').addEventListener('input', updateFare);

document.getElementById('booking-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!selectedTrain) return;

    const confirmBtn = document.getElementById('confirm-btn');
    confirmBtn.disabled = true;
    confirmBtn.textContent = 'Processing...';
    showBanner('success', 'Request added to booking queue, processing...');

    const payload = {
        trainId: selectedTrain.id,
        passengerName: document.getElementById('p-name').value.trim(),
        age: document.getElementById('p-age').value,
        gender: document.getElementById('p-gender').value,
        mobile: document.getElementById('p-mobile').value.trim(),
        email: document.getElementById('p-email').value.trim(),
        passengers: document.getElementById('p-count').value,
        date: journeyDate,
        travelClass: journeyClass
    };

    try {
        const data = await apiBook(payload);
        if (data.status === 'success') {
            showBanner('success', data.message);
            setText('ticket-pnr', data.pnr);
            setText('ticket-coach', data.coach);
            setText('ticket-seat', data.seat);
            document.getElementById('ticket-box').style.display = 'block';
            setText('sum-seats', data.seatsLeft);
            confirmBtn.textContent = 'Booked';
        } else {
            showBanner('error', data.message);
            confirmBtn.disabled = false;
            confirmBtn.textContent = 'Confirm Booking';
        }
    } catch (err) {
        showBanner('error', 'Error: Could not communicate with Java backend.');
        confirmBtn.disabled = false;
        confirmBtn.textContent = 'Confirm Booking';
    }
});

loadTrain();