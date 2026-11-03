import { findings, BEHAVIOR_WARNING } from "./report-data.js";

export const FLAG_ORDER = ["attack", "keybind", "hotbar", "use"];
export const FLAG_LABELS = {
  attack: "Attack",
  keybind: "Key Press",
  hotbar: "Hotbar Switch",
  use: "Item Use",
};

const AVATAR_FALLBACK = "https://mc-heads.net/avatar/MHF_Steve/48";

export function localTime(s) {
  if (!s) return "";
  const iso = s.replace(" ", "T").replace(" UTC", "Z");
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? s : d.toLocaleString();
}

export function avatar(r, size = 48) {
  const img = document.createElement("img");
  img.className = "avatar";
  img.width = size;
  img.height = size;
  img.loading = "lazy";
  img.alt = "";
  const uuid = encodeURIComponent(r.uuid || "");
  img.src = `https://mc-heads.net/avatar/${uuid || "MHF_Steve"}/${size}`;
  img.addEventListener(
    "error",
    () => {
      img.src = AVATAR_FALLBACK;
    },
    { once: true },
  );
  return img;
}

export function sourceLabel(s) {
  if (s.type === "MOD") return s.modName || s.modId || "Unknown mod";
  if (s.type === "EXTERNAL") {
    return s.suspects && s.suspects.length
      ? `Source unknown · running: ${s.suspects.join(", ")}`
      : "Source unknown";
  }
  return "Unattributed";
}

function bySource(sources) {
  const groups = new Map();
  for (const s of sources) {
    if (!s || typeof s !== "object") continue;
    const type = s.type === "MOD" || s.type === "EXTERNAL" ? s.type : "UNKNOWN";
    const label = sourceLabel(s);
    const key = `${type}|${label}`;
    let g = groups.get(key);
    if (!g) {
      g = {
        label,
        type,
        count: 0,
        version: s.modVersion || "",
        kinds: [],
        details: new Set(),
      };
      groups.set(key, g);
    }
    const n = Number.parseInt(s.count, 10) || 0;
    g.count += n;
    const kind = FLAG_LABELS[s.kind];
    if (kind) g.kinds.push(n > 0 ? `${kind} ×${n}` : kind);
    if (s.detail) g.details.add(s.detail);
  }
  return [...groups.values()].sort((a, b) => b.count - a.count);
}

export function sourceChips(r) {
  const groups = bySource(Array.isArray(r.sources) ? r.sources : []);
  if (!groups.length) return null;
  const row = document.createElement("div");
  row.className = "sources";
  for (const g of groups) {
    const chip = document.createElement("span");
    chip.className = `chip chip--src src--${g.type.toLowerCase()}`;
    chip.textContent = g.count > 0 ? `${g.label} ×${g.count}` : g.label;
    const tip = [g.kinds.join(", "), g.version && `v${g.version}`, ...g.details]
      .filter(Boolean)
      .join("  :  ");
    if (tip) chip.title = tip;
    row.appendChild(chip);
  }
  return row;
}

export function flagChips(r) {
  const chips = document.createElement("div");
  chips.className = "chips";
  const flags = r.flags || {};
  let any = false;
  for (const key of FLAG_ORDER) {
    const n = Number.parseInt(flags[key], 10) || 0;
    if (n > 0) {
      any = true;
      const chip = document.createElement("span");
      chip.className = `chip chip--${key}`;
      chip.textContent = `${FLAG_LABELS[key]} ×${n}`;
      chips.appendChild(chip);
    }
  }
  if (!any && !findings(r).length && Array.isArray(r.detected)) {
    for (const d of r.detected) {
      if (/^(Behavioral Evidence|Windows Software Generated Input)$/i.test(String(d))) continue;
      const chip = document.createElement("span");
      chip.className = "chip";
      chip.textContent = String(d).replace(/^Synthetic /, "Automated ");
      chips.appendChild(chip);
    }
  }
  return chips;
}

function externalEvidence(report) {
  const rows = findings(report);
  if (!rows.length) return null;
  const wrap = document.createElement("div");
  wrap.className = "external-evidence";
  for (const category of ["OBSERVED_INPUT", "BEHAVIORAL"]) {
    const items = rows.filter((finding) => finding.category === category);
    if (!items.length) continue;
    const group = document.createElement("div");
    group.className = category === "BEHAVIORAL" ? "evidence-group evidence-group--behavioral" : "evidence-group";
    const title = document.createElement("strong");
    title.className = "evidence-group__title";
    title.textContent = category === "BEHAVIORAL" ? "Behavioral Evidence" : "External Input";
    const note = document.createElement("p");
    note.className = "evidence-note";
    note.textContent = category === "BEHAVIORAL" ? "Not definitive proof of cheating."
      : "Windows marked this input as software generated; legitimate tools can also cause it.";
    if (category === "BEHAVIORAL") group.setAttribute("aria-label", BEHAVIOR_WARNING);
    const list = document.createElement("ul");
    list.className = "evidence-list";
    for (const finding of items) {
      const entry = document.createElement("li");
      const nativeLabels = { "windows-0": "Mouse buttons", "windows-1": "Key presses", "windows-2": "Pointer movement", "windows-3": "Scrolling" };
      entry.textContent = category === "OBSERVED_INPUT" && nativeLabels[finding.id]
        ? nativeLabels[finding.id] : String(finding.label || "Unspecified observation").replace("target-acquisition", "target acquisition");
      entry.title = finding.detail || "";
      const amount = document.createElement("span");
      const samples = Math.max(0, Number(finding.samples) || 0);
      amount.textContent = `${samples.toLocaleString()} ${category === "BEHAVIORAL" ? "samples" : "events"}`;
      entry.appendChild(amount);
      list.appendChild(entry);
    }
    group.append(title, note, list);
    wrap.appendChild(group);
  }
  return wrap;
}

export function card(r, opts = {}) {
  const el = document.createElement("article");
  el.className = "card";
  el.dataset.id = r.id;

  const head = document.createElement("div");
  head.className = "card__head";

  if (opts.onSelect) {
    const box = document.createElement("input");
    box.type = "checkbox";
    box.className = "card-select";
    box.dataset.id = r.id;
    box.checked = Boolean(opts.selected && opts.selected(r.id));
    box.addEventListener("change", () => opts.onSelect(r.id, box.checked, el));
    head.appendChild(box);
  }

  const who = document.createElement("div");
  who.className = "who";
  const name = document.createElement("div");
  name.className = "name";
  name.textContent = r.player || "unknown";
  name.title = name.textContent;
  const server = document.createElement("div");
  server.className = "server";
  server.textContent = r.server || "unknown";
  server.title = server.textContent;
  who.append(name, server);

  const acctVal =
    r.accountType === "Premium" || r.accountType === "Cracked"
      ? r.accountType
      : "Unknown";
  const acct = document.createElement("span");
  acct.className = `acct acct--${acctVal.toLowerCase()}`;
  acct.textContent = acctVal;
  acct.title = "Account type";

  who.appendChild(acct);
  head.append(avatar(r), who);

  const foot = document.createElement("div");
  foot.className = "card__foot";
  const time = document.createElement("span");
  time.className = "time";
  const rawTime = r.endUtc || r.startUtc || "";
  time.textContent = localTime(rawTime);
  time.title = rawTime;

  const actions = document.createElement("div");
  actions.className = "actions";
  if (opts.onDelete) {
    const del = document.createElement("button");
    del.className = "del";
    del.type = "button";
    del.textContent = "Delete";
    del.addEventListener("click", () => opts.onDelete(r, el, del));
    actions.appendChild(del);
  }
  const open = document.createElement("a");
  open.className = "open";
  open.textContent = "Open report";
  open.href = `/report/${encodeURIComponent(r.id || "")}`;
  open.target = "_blank";
  open.rel = "noopener";
  actions.appendChild(open);

  foot.append(time, actions);

  const evidence = document.createElement("div");
  evidence.className = "card__evidence";
  const flags = flagChips(r);
  if (flags.childElementCount) {
    const label = document.createElement("strong");
    label.className = "evidence-group__title";
    label.textContent = "Automated Input";
    evidence.append(label, flags);
  }
  const external = externalEvidence(r);
  if (external) evidence.appendChild(external);
  const sources = sourceChips(r);
  if (sources) {
    const details = document.createElement("details");
    details.className = "source-details";
    const summary = document.createElement("summary");
    summary.textContent = "Source context";
    details.append(summary, sources);
    evidence.appendChild(details);
  }
  el.append(head, evidence, foot);
  return el;
}
