import { sanitizeExternalFindings } from "./evidence.mjs";

const oldSection = /<section\b[^>]*>\s*<h2>External input and Behavioral Evidence<\/h2>[\s\S]*?<\/section>/;
const escape = (value) => String(value ?? "").replace(/[&<>"']/g, (character) => ({
  "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
})[character]);
const nativeLabels = {
  "windows-0": "Mouse buttons", "windows-1": "Key presses",
  "windows-2": "Pointer movement", "windows-3": "Scrolling",
};
const descriptions = {
  "shield-response": "Axe swaps repeatedly lined up with opponents starting to block.",
  "shield-specific": "Axe use was almost exclusive to blocking opponents, with few recorded misses and repeated stops in blocking after the attacks.",
  "target-timing": "Fresh attacks repeatedly followed separate target acquisitions with unusually similar delays.",
  "shield-sequence": "Repeated sword, axe, attack, and switch back sequences had unusually consistent timing.",
  "aim": "Repeated large aim corrections ended very close to the same target's centre.",
};

export function upgradeEvidenceReport(html, canonical) {
  if (!oldSection.test(html)) return html;
  let data;
  try { data = typeof canonical === "string" ? JSON.parse(canonical) : canonical; }
  catch { return html; }
  if (!data || !Array.isArray(data.externalFindings)) return html;
  const findings = sanitizeExternalFindings(data.externalFindings);
  const observed = findings.filter((finding) => finding.category === "OBSERVED_INPUT");
  const behavioral = findings.filter((finding) => finding.category === "BEHAVIORAL");
  const nativeCards = observed.map((finding) => `<article class="input-observation"><span class="input-observation__count">${finding.count.toLocaleString("en-US")}</span><h3>${escape(nativeLabels[finding.id] || finding.label)}</h3><span class="evidence-unit">events recorded</span></article>`).join("");
  const behaviorCards = behavioral.map((finding) => `<article class="behavior-finding"><div class="behavior-finding__head"><h3>${escape(finding.label.replace("target-acquisition", "target acquisition"))}</h3><span class="evidence-samples">${finding.samples.toLocaleString("en-US")} samples</span></div><p>${escape(descriptions[finding.id] || "A repeated gameplay pattern was recorded for review.")}</p><details class="evidence-details"><summary>View measurements</summary><p>${escape(finding.detail)}</p></details></article>`).join("");
  const rawScore = Number(data.behaviorScore);
  const score = Number.isFinite(rawScore) ? Math.min(100, behavioral.reduce((sum, finding) => sum + finding.score, 0), Math.max(0, Math.round(rawScore))) : 0;
  const coverage = typeof data.externalCoverage === "string" ? data.externalCoverage.slice(0, 160) : "No Windows monitoring status recorded.";
  const replacement = `<section class="report-evidence"><h2>External Input</h2><p class="evidence-intro">Windows input observations recorded while Minecraft gameplay was focused.</p>${observed.length ? `<div class="input-observations">${nativeCards}</div>` : '<p class="evidence-empty">No Windows input observations recorded.</p>'}<details class="evidence-details"><summary>Monitoring details</summary><p>${escape(coverage)}</p><p>Software generated input can come from legitimate remappers or accessibility tools. These observations do not identify the originating program and are not proof of cheating. Hardware and driver level input may not be detected.</p></details></section><section class="report-evidence"><h2>Behavioral Evidence</h2><p class="behavior-disclaimer"><strong>Not definitive proof of cheating.</strong> These experimental patterns can also occur during legitimate play.</p>${behavioral.length ? `<div class="behavior-findings">${behaviorCards}</div>` : '<p class="evidence-empty">No behavioral findings recorded.</p>'}<details class="evidence-details"><summary>About this evidence</summary><p>Review priority: ${score} / 100. This is not a probability of cheating or a ban recommendation. Older contributions decay over time; recorded findings remain in the session history.</p><p>No findings does not establish that the client is free of cheats.</p></details></section>`;
  let updated = html.replace(oldSection, () => replacement);
  if (!updated.includes('href="/report-evidence.css"')) {
    updated = updated.replace("</head>", '<link rel="stylesheet" href="/report-evidence.css"></head>');
  }
  return updated;
}
