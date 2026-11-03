export const BEHAVIOR_WARNING = "Behavioral Evidence. Not definitive proof of cheating.";

export function findings(report) {
  return Array.isArray(report.externalFindings) ? report.externalFindings.filter((finding) => finding && !["click-regularity", "click-pattern"].includes(finding.id)) : [];
}

export function hasBehavioral(report) {
  return findings(report).some((finding) => finding.category === "BEHAVIORAL");
}

export function hasWindowsInput(report) {
  return findings(report).some((finding) => finding.category === "OBSERVED_INPUT");
}

function timestamp(report) {
  const raw = report.endUtc || report.startUtc || "";
  const parsed = Date.parse(raw.replace(" ", "T").replace(" UTC", "Z"));
  return Number.isFinite(parsed) ? parsed : 0;
}

export function filterReports(reports, { query = "", evidence = "all", sort = "newest" } = {}) {
  const q = query.trim().toLowerCase();
  const selected = reports.filter((report) => {
    if (evidence === "behavioral" && !hasBehavioral(report)) return false;
    if (evidence === "windows" && !hasWindowsInput(report)) return false;
    if (evidence === "automated" && !Object.values(report.flags || {}).some((value) => Number(value) > 0)) return false;
    if (!q) return true;
    const text = [report.player, report.server, ...(report.detected || []),
      ...findings(report).map((finding) => finding.label),
      ...(report.sources || []).map((source) => source.modName || source.modId || "")];
    return text.some((value) => String(value || "").toLowerCase().includes(q));
  });
  return selected.sort((a, b) => sort === "player"
    ? String(a.player || "").localeCompare(String(b.player || "")) || timestamp(b) - timestamp(a)
    : sort === "oldest" ? timestamp(a) - timestamp(b) : timestamp(b) - timestamp(a));
}
