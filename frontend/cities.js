// City list sourced from the project's cityData.js — used to power
// <datalist> autocomplete on the From/To station inputs.
const CITIES = [
    "Delhi", "Mumbai", "Kolkata", "Chennai", "Bengaluru", "Hyderabad", "Pune",
    "Ahmedabad", "Jaipur", "Lucknow", "Kanpur", "Nagpur", "Indore", "Bhopal",
    "Patna", "Ranchi", "Varanasi", "Prayagraj", "Agra", "Mathura", "Chandigarh",
    "Amritsar", "Ludhiana", "Jammu", "Srinagar", "Dehradun", "Haridwar", "Surat",
    "Vadodara", "Rajkot", "Udaipur", "Jodhpur", "Kota", "Nashik", "Aurangabad",
    "Goa", "Kochi", "Trivandrum", "Mysore", "Coimbatore"
];

// Injects a <datalist> of cities and wires it to every input with
// the "city-input" class via the list="city-list" attribute.
function setupCityAutocomplete() {
    if (!document.getElementById('city-list')) {
        const datalist = document.createElement('datalist');
        datalist.id = 'city-list';
        datalist.innerHTML = CITIES.map(c => `<option value="${c}"></option>`).join('');
        document.body.appendChild(datalist);
    }
    document.querySelectorAll('.city-input').forEach(input => {
        input.setAttribute('list', 'city-list');
    });
}