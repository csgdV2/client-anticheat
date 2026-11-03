import { card } from "./report-card.js";
import { filterReports } from "./report-data.js";

const grid = document.getElementById("grid");
const statusEl = document.getElementById("status");
const search = document.getElementById("search");
const moreWrap = document.querySelector(".more");
const loadMore = document.getElementById("loadMore");

const evidenceFilter = document.getElementById("evidenceFilter");
const sortOrder = document.getElementById("sortOrder");
const viewMode = document.getElementById("viewMode");
viewMode.value = "cards";
grid.classList.add("grid--cards");
const PAGE = 24;

let all = [];
let shown = [];
let page = 1;

async function load() {
  try {
    const res = await fetch("/api/reports");
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    all = Array.isArray(data.reports) ? data.reports : [];
    apply();
  } catch {
    statusEl.textContent = "Could not load reports.";
    statusEl.style.display = "";
  }
}

// A search shows every match at once so a player's full history is visible;
// browsing without a search pages through the list.
function apply() {
  shown = filterReports(all, { query: search.value, evidence: evidenceFilter.value, sort: sortOrder.value });
  page = 1;
  render();
}

function render() {
  const searching = search.value.trim() !== "";
  const limit = searching ? shown.length : page * PAGE;
  const slice = shown.slice(0, limit);
  document.getElementById("resultCount").textContent = `${shown.length.toLocaleString()} matching ${shown.length === 1 ? "session" : "sessions"}`;

  grid.replaceChildren();
  if (!slice.length) {
    statusEl.textContent = all.length
      ? "No sessions match these filters. Try a different search or evidence type."
      : "No reports yet.";
    statusEl.style.display = "";
    moreWrap.hidden = true;
    return;
  }
  statusEl.style.display = "none";
  const frag = document.createDocumentFragment();
  for (const r of slice) frag.appendChild(card(r));
  grid.appendChild(frag);

  const remaining = shown.length - slice.length;
  moreWrap.hidden = remaining <= 0;
  if (remaining > 0) loadMore.textContent = `Load more (${remaining})`;
}

loadMore.addEventListener("click", () => {
  page += 1;
  render();
});

evidenceFilter.addEventListener("change", apply);
sortOrder.addEventListener("change", apply);
viewMode.addEventListener("change", () => grid.classList.toggle("grid--cards", viewMode.value === "cards"));

let debounce;
search.addEventListener("input", () => {
  clearTimeout(debounce);
  debounce = setTimeout(apply, 120);
});

load();
