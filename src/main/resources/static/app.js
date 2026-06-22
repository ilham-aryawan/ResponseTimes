let contacts = [
  {
    name: "Maya Chen",
    initials: "MC",
    handle: "Close friend · 186 messages",
    time: 4,
    unit: "min",
    avatar: "avatar-maya",
    responseRate: "96%",
    bestTime: "6–8 PM",
    conversations: 38,
    streak: "12 days",
    insight: "Maya is fastest in the evening and usually replies within 2 minutes on Wednesdays."
  },
  {
    name: "Noah Williams",
    initials: "NW",
    handle: "Work · 142 messages",
    time: 11,
    unit: "min",
    avatar: "avatar-noah",
    responseRate: "91%",
    bestTime: "9–11 AM",
    conversations: 31,
    streak: "6 days",
    insight: "Morning messages get the quickest response. Mondays tend to be slower than the rest of the week."
  },
  {
    name: "Elena Rossi",
    initials: "ER",
    handle: "Family · 118 messages",
    time: 18,
    unit: "min",
    avatar: "avatar-elena",
    responseRate: "89%",
    bestTime: "7–9 PM",
    conversations: 26,
    streak: "9 days",
    insight: "Elena is reliably responsive after dinner, with very little variation between weekdays."
  },
  {
    name: "Jordan Lee",
    initials: "JL",
    handle: "Friend · 97 messages",
    time: 27,
    unit: "min",
    avatar: "avatar-jordan",
    responseRate: "84%",
    bestTime: "12–2 PM",
    conversations: 24,
    streak: "4 days",
    insight: "Jordan often replies around lunch. Weekend response times are about 14 minutes slower."
  },
  {
    name: "Liam Brooks",
    initials: "LB",
    handle: "Work · 84 messages",
    time: 42,
    unit: "min",
    avatar: "avatar-liam",
    responseRate: "76%",
    bestTime: "3–5 PM",
    conversations: 19,
    streak: "3 days",
    insight: "Liam batches replies in the late afternoon. Messages sent before noon often wait until after 3 PM."
  },
  {
    name: "Priya Kapoor",
    initials: "PK",
    handle: "Friend · 72 messages",
    time: 1.2,
    unit: "hr",
    avatar: "avatar-priya",
    responseRate: "71%",
    bestTime: "8–10 PM",
    conversations: 17,
    streak: "2 days",
    insight: "Priya is most active later at night. Her reply window is more predictable on weekends."
  },
  {
    name: "Alex Morgan",
    initials: "AM",
    handle: "Community · 58 messages",
    time: 2.4,
    unit: "hr",
    avatar: "avatar-alex",
    responseRate: "63%",
    bestTime: "10 AM–12 PM",
    conversations: 12,
    streak: "1 day",
    insight: "Alex tends to respond in focused blocks. Mid-morning messages have the best chance of a same-hour reply."
  }
];

let chartPeriods = {
  7: {
    values: [21, 30, 18, 26, 15, 32, 24],
    baseline: [34, 38, 27, 31, 25, 40, 36],
    labels: ["Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed"],
    median: "24 min"
  },
  30: {
    values: [42, 36, 47, 30, 32, 24, 38, 27, 20, 24],
    baseline: [48, 45, 44, 39, 42, 37, 41, 35, 33, 36],
    labels: ["May 12", "15", "18", "21", "24", "27", "30", "Jun 2", "5", "8"],
    median: "24 min"
  },
  90: {
    values: [52, 49, 44, 48, 39, 42, 35, 33, 29, 31, 24, 26],
    baseline: [55, 51, 53, 47, 48, 43, 45, 39, 40, 36, 35, 34],
    labels: ["Mar", "", "Late Mar", "", "Apr", "", "Late Apr", "", "May", "", "Late May", "Jun"],
    median: "32 min"
  }
};

let heatmapData = [
  [0, 0, 1, 1, 2, 3, 4, 2],
  [0, 1, 1, 2, 2, 3, 4, 3],
  [0, 1, 2, 2, 3, 4, 5, 4],
  [0, 1, 2, 3, 3, 4, 5, 4],
  [0, 1, 1, 2, 3, 3, 4, 5],
  [0, 0, 1, 1, 2, 3, 4, 4],
  [0, 0, 1, 2, 2, 3, 4, 3]
];

let days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
let hours = ["8a", "10a", "12p", "2p", "4p", "6p", "8p", "10p"];

const contactList = document.querySelector("#contactList");
const emptyState = document.querySelector("#emptyState");
const searchInput = document.querySelector("#contactSearch");
const drawer = document.querySelector("#contactDrawer");
const drawerContent = document.querySelector("#drawerContent");
const importModal = document.querySelector("#importModal");
const toast = document.querySelector("#toast");
let toastTimer;

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function splitDurationLabel(label) {
  const [value, unit = ""] = String(label).split(" ");
  return { value, unit };
}

function activePeriod() {
  return Number(document.querySelector("#periodControl button.active")?.dataset.period || 30);
}

function renderSummary(summary) {
  if (!summary) return;

  const median = splitDurationLabel(summary.medianResponseTime);
  document.querySelector("#medianStat").innerHTML = `${escapeHtml(median.value)} <span>${escapeHtml(median.unit)}</span>`;
  document.querySelector("#medianTrend").lastChild.textContent = ` ${summary.medianTrend}`;
  document.querySelector("#conversationStat").textContent = summary.conversationCount;
  document.querySelector("#conversationDetail").textContent = summary.conversationDetail;
  document.querySelector("#fastestName").textContent = summary.fastestName;
  document.querySelector("#fastestDetail").textContent = summary.fastestDetail;

  const bestTime = splitDurationLabel(summary.bestTime);
  document.querySelector("#bestTimeStat").innerHTML = `${escapeHtml(bestTime.value)} <span>${escapeHtml(bestTime.unit)}</span>`;
  document.querySelector("#bestTimeDetail").textContent = summary.bestTimeDetail;
}

function renderContacts(list = contacts) {
  contactList.innerHTML = list.map((contact) => {
    const originalRank = contacts.findIndex((item) => item.id === contact.id || item.name === contact.name) + 1;
    const contactKey = contact.id || contact.name;
    return `
      <button class="contact-row" type="button" data-contact="${escapeHtml(contactKey)}" aria-label="View ${escapeHtml(contact.name)}'s response details">
        <span class="contact-rank">${String(originalRank).padStart(2, "0")}</span>
        <span class="avatar ${escapeHtml(contact.avatar)}">${escapeHtml(contact.initials)}</span>
        <span class="contact-meta">
          <strong>${escapeHtml(contact.name)}</strong>
          <span>${escapeHtml(contact.handle)}</span>
        </span>
        <span class="response-time">
          <strong>${escapeHtml(contact.time)}</strong>
          <span>${escapeHtml(contact.unit)} median</span>
        </span>
        <svg class="contact-arrow" viewBox="0 0 20 20" aria-hidden="true"><path d="m7 4 6 6-6 6" /></svg>
      </button>
    `;
  }).join("");

  emptyState.hidden = list.length > 0;
}

function openContact(contactKey) {
  const contact = contacts.find((item) => item.id === contactKey || item.name === contactKey);
  if (!contact) return;

  drawerContent.innerHTML = `
    <div class="drawer-content">
      <span class="avatar drawer-avatar ${escapeHtml(contact.avatar)}">${escapeHtml(contact.initials)}</span>
      <span class="section-kicker">CONTACT DETAILS</span>
      <h2>${escapeHtml(contact.name)}</h2>
      <p>${escapeHtml(contact.handle)}</p>

      <div class="drawer-highlight">
        <span>Median response time</span>
        <strong>${escapeHtml(contact.time)} ${escapeHtml(contact.unit)}</strong>
        <small>Based on the last 90 days</small>
      </div>

      <div class="drawer-grid">
        <div class="drawer-stat"><span>Response rate</span><strong>${escapeHtml(contact.responseRate)}</strong></div>
        <div class="drawer-stat"><span>Best time</span><strong>${escapeHtml(contact.bestTime)}</strong></div>
        <div class="drawer-stat"><span>Conversations</span><strong>${escapeHtml(contact.conversations)}</strong></div>
        <div class="drawer-stat"><span>Current streak</span><strong>${escapeHtml(contact.streak)}</strong></div>
      </div>

      <div class="drawer-insight">
        <strong>Timing insight</strong>
        ${escapeHtml(contact.insight)}
      </div>
    </div>
  `;

  drawer.classList.add("open");
  drawer.setAttribute("aria-hidden", "false");
  drawer.inert = false;
  document.querySelector("#closeDrawer").focus();
}

function closeDrawer() {
  drawer.classList.remove("open");
  drawer.setAttribute("aria-hidden", "true");
  drawer.inert = true;
}

function getPath(values, width = 700, height = 240, max = 60) {
  return values.map((value, index) => {
    const x = (index / (values.length - 1)) * width;
    const y = height - (value / max) * height;
    return `${index === 0 ? "M" : "L"} ${x.toFixed(1)} ${y.toFixed(1)}`;
  }).join(" ");
}

function renderChart(period = 30) {
  const data = chartPeriods[period];
  if (!data) return;

  const chartMax = Math.max(60, ...data.values, ...data.baseline);
  const linePath = getPath(data.values, 700, 240, chartMax);
  const areaPath = `${linePath} L 700 240 L 0 240 Z`;
  document.querySelector("#dataPath").setAttribute("d", linePath);
  document.querySelector("#areaPath").setAttribute("d", areaPath);
  document.querySelector("#baselinePath").setAttribute("d", getPath(data.baseline, 700, 240, chartMax));
  document.querySelector("#chartMedian").textContent = data.median;

  document.querySelector("#dataPoints").innerHTML = data.values.map((value, index) => {
    const x = (index / (data.values.length - 1)) * 700;
    const y = 240 - (value / chartMax) * 240;
    return `<circle class="data-point" data-index="${index}" cx="${x}" cy="${y}" r="4" />`;
  }).join("");

  document.querySelector("#chartLabels").innerHTML = data.labels
    .map((label) => `<span>${escapeHtml(label)}</span>`)
    .join("");

  const tooltip = document.querySelector("#chartTooltip");
  document.querySelectorAll(".data-point").forEach((point) => {
    point.addEventListener("mouseenter", (event) => {
      const index = Number(event.target.dataset.index);
      const rect = event.target.getBoundingClientRect();
      const plotRect = document.querySelector("#responseChart").getBoundingClientRect();
      tooltip.innerHTML = `<strong>${data.values[index]} min</strong><br>${data.labels[index] || "This period"}`;
      tooltip.style.left = `${rect.left - plotRect.left + rect.width / 2}px`;
      tooltip.style.top = `${rect.top - plotRect.top}px`;
      tooltip.hidden = false;
    });
    point.addEventListener("mouseleave", () => {
      tooltip.hidden = true;
    });
  });
}

function renderHeatmap() {
  const html = [
    `<span class="heatmap-label"></span>`,
    ...hours.map((hour) => `<span class="heatmap-hour">${hour}</span>`)
  ];

  heatmapData.forEach((row, rowIndex) => {
    html.push(`<span class="heatmap-label">${days[rowIndex]}</span>`);
    row.forEach((level, columnIndex) => {
      html.push(
        `<span class="heatmap-cell heat-${level}" title="${days[rowIndex]} at ${hours[columnIndex]}: ${level === 0 ? "very low" : `level ${level}`} activity"></span>`
      );
    });
  });

  document.querySelector("#heatmap").innerHTML = html.join("");
}

function showToast(message) {
  document.querySelector("#toastMessage").textContent = message;
  toast.classList.add("show");
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => toast.classList.remove("show"), 2600);
}

function openImportModal() {
  importModal.hidden = false;
  document.body.classList.add("modal-open");
  document.querySelector("#closeImport").focus();
}

function closeImportModal() {
  importModal.hidden = true;
  document.body.classList.remove("modal-open");
}

async function loadDashboard({ silent = false } = {}) {
  try {
    const response = await fetch("/api/dashboard", {
      headers: { Accept: "application/json" }
    });
    if (!response.ok) {
      throw new Error(`Dashboard API returned ${response.status}`);
    }

    const data = await response.json();
    contacts = data.contacts || contacts;
    chartPeriods = data.activity?.chartPeriods || chartPeriods;
    heatmapData = data.activity?.heatmap?.values || heatmapData;
    days = data.activity?.heatmap?.days || days;
    hours = data.activity?.heatmap?.hours || hours;

    renderSummary(data.summary);
    renderContacts(filteredContacts());
    renderChart(activePeriod());
    renderHeatmap();
  } catch (error) {
    console.warn(error);
    if (!silent) {
      showToast("Using local demo data. Start Spring Boot for live APIs.");
    }
  }
}

function filteredContacts() {
  const query = searchInput.value.trim().toLowerCase();
  return contacts.filter((contact) =>
    `${contact.name} ${contact.handle}`.toLowerCase().includes(query)
  );
}

async function uploadFile(file) {
  const formData = new FormData();
  formData.append("file", file);

  closeImportModal();
  showToast(`Importing ${file.name} locally...`);

  try {
    const response = await fetch("/api/import", {
      method: "POST",
      body: formData
    });
    if (!response.ok) {
      throw new Error(`Import failed with ${response.status}`);
    }
    const result = await response.json();
    await loadDashboard({ silent: true });
    showToast(result.message);
  } catch (error) {
    console.warn(error);
    showToast(`${file.name} selected. Start Spring Boot to analyze it.`);
  }
}

async function loadDemoData() {
  closeImportModal();
  try {
    const response = await fetch("/api/import/demo", { method: "POST" });
    if (!response.ok) {
      throw new Error(`Demo import failed with ${response.status}`);
    }
    const result = await response.json();
    await loadDashboard({ silent: true });
    showToast(result.message);
  } catch (error) {
    console.warn(error);
    renderContacts();
    renderChart(activePeriod());
    renderHeatmap();
    showToast("Demo data loaded locally.");
  }
}

async function importFromImessage() {
  closeImportModal();
  showToast("Reading Messages locally...");

  try {
    const response = await fetch("/api/import/imessage", { method: "POST" });
    const result = await response.json();
    if (!response.ok) {
      throw new Error(result.message || `iMessage import failed with ${response.status}`);
    }
    await loadDashboard({ silent: true });
    showToast(result.message);
  } catch (error) {
    console.warn(error);
    showToast(error.message || "Could not read iMessage. Check Full Disk Access and restart Spring Boot.");
  }
}

async function downloadImessageCsv() {
  showToast("Preparing timing CSV locally...");

  try {
    const response = await fetch("/api/export/imessage.csv");
    if (!response.ok) {
      const result = await response.json();
      throw new Error(result.message || `CSV export failed with ${response.status}`);
    }

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "replywise-imessage-timing.csv";
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    showToast("Timing CSV downloaded.");
  } catch (error) {
    console.warn(error);
    showToast(error.message || "Could not export iMessage CSV. Check Full Disk Access.");
  }
}

renderContacts();
renderChart();
renderHeatmap();
loadDashboard({ silent: true });

searchInput.addEventListener("input", (event) => {
  renderContacts(filteredContacts());
});

contactList.addEventListener("click", (event) => {
  const row = event.target.closest(".contact-row");
  if (row) openContact(row.dataset.contact);
});

document.querySelector("#closeDrawer").addEventListener("click", closeDrawer);

document.querySelector("#periodControl").addEventListener("click", (event) => {
  const button = event.target.closest("button");
  if (!button) return;
  document.querySelectorAll("#periodControl button").forEach((item) => {
    item.classList.remove("active");
    item.setAttribute("aria-pressed", "false");
  });
  button.classList.add("active");
  button.setAttribute("aria-pressed", "true");
  renderChart(Number(button.dataset.period));
});

document.querySelector("#openImport").addEventListener("click", openImportModal);
document.querySelector("#closeImport").addEventListener("click", closeImportModal);
document.querySelector("#demoImport").addEventListener("click", loadDemoData);
document.querySelector("#imessageImport").addEventListener("click", importFromImessage);
document.querySelector("#imessageCsv").addEventListener("click", downloadImessageCsv);

importModal.addEventListener("click", (event) => {
  if (event.target === importModal) closeImportModal();
});

const dropZone = document.querySelector("#dropZone");
["dragenter", "dragover"].forEach((eventName) => {
  dropZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    dropZone.classList.add("dragging");
  });
});

["dragleave", "drop"].forEach((eventName) => {
  dropZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    dropZone.classList.remove("dragging");
  });
});

dropZone.addEventListener("drop", (event) => {
  const file = event.dataTransfer.files[0];
  if (file) {
    uploadFile(file);
  }
});

document.querySelector("#fileInput").addEventListener("change", (event) => {
  const file = event.target.files[0];
  if (file) {
    uploadFile(file);
  }
});

document.querySelector("#viewAllButton").addEventListener("click", () => {
  searchInput.value = "";
  renderContacts();
  searchInput.focus();
  showToast(`Showing all ${contacts.length} analyzed contacts.`);
});

document.querySelector("#notificationButton").addEventListener("click", () => {
  showToast("No new insights right now. You’re all caught up.");
});

document.querySelector("#profileButton").addEventListener("click", () => {
  showToast("Profile controls are ready for a connected account.");
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    if (!importModal.hidden) closeImportModal();
    closeDrawer();
  }
});
