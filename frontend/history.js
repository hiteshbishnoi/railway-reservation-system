if (!requireAuth()) {
    throw new Error("Not authenticated");
}

renderNavbar("history");

// Delete Ticket
async function deleteBooking(pnr) {

    const ok = confirm("Are you sure you want to delete this ticket?");

    if (!ok) return;

    try {

        await fetch(`${API_URL}/history?pnr=${pnr}`, {
            method: "DELETE"
        });

        loadHistory(document.getElementById("pnr-search").value.trim());

    } catch (err) {

        alert("Unable to delete ticket.");

    }
}

function historyCardHtml(r) {

    const success = r.status === "success";

    return `

    <div class="history-card">

        <div class="history-header">

            <div>

                <h2>🚆 ${r.trainName}</h2>

                <div class="pnr">
                    PNR : <strong>${r.pnr || "-"}</strong>
                </div>

            </div>

            <span class="status-badge ${success ? "status-success" : "status-error"}">

                ${success ? "✅ Confirmed" : "❌ Failed"}

            </span>

        </div>

        <div class="history-grid">

            <div>
                <strong>Passenger</strong>
                <p>${r.passengerName}</p>
            </div>

            <div>
                <strong>Train No.</strong>
                <p>${r.trainNo || "-"}</p>
            </div>

            <div>
                <strong>Route</strong>
                <p>${r.from} ➜ ${r.to}</p>
            </div>

            <div>
                <strong>Class</strong>
                <p>${r.travelClass || "-"}</p>
            </div>

            <div>
                <strong>Seat</strong>
                <p>${success ? `${r.coach} / ${r.seat}` : "-"}</p>
            </div>

            <div>
                <strong>Fare</strong>
                <p>₹${r.fare || 0}</p>
            </div>

        </div>

        <div class="history-footer">

            <button
                class="delete-btn"
                onclick="deleteBooking('${r.pnr}')">

                🗑 Delete Ticket

            </button>

        </div>

    </div>

    `;
}

function renderHistory(records) {

    const container = document.getElementById("history-container");

    if (!records || records.length === 0) {

        container.innerHTML = `

            <div class="history-empty">

                <h2>🎫 No Booking Found</h2>

                <p>
                    You haven't booked any train yet.
                </p>

            </div>

        `;

        return;
    }

    container.innerHTML = records.map(historyCardHtml).join("");

}

async function loadHistory(pnr = "") {

    const container = document.getElementById("history-container");

    container.innerHTML = `<p class="loading">Loading...</p>`;

    try {

        const records = await apiGetHistory(pnr);

        renderHistory(records);

    } catch (err) {

        container.innerHTML = `

        <div class="history-empty">

            <h2 style="color:#ef4444;">
                Backend Connection Failed
            </h2>

            <p>
                Please start BackendServer.java
            </p>

        </div>

        `;

    }
}

let debounce;

document.getElementById("pnr-search").addEventListener("input", function () {

    clearTimeout(debounce);

    debounce = setTimeout(() => {

        loadHistory(this.value.trim());

    }, 300);

});

loadHistory();