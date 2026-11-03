const text = (value, max) => typeof value === "string" ? value.slice(0, max) : "";
const count = (value, max = 1_000_000) => {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed > 0 ? Math.min(max, parsed) : 0;
};

export function sanitizeExternalFindings(values) {
  if (!Array.isArray(values)) return [];
  return values.slice(0, 12).filter((value) => value && typeof value === "object"
    && !["click-regularity", "click-pattern"].includes(value.id)
    && ["BEHAVIORAL", "OBSERVED_INPUT"].includes(value.category)).map((value) => ({
    id: text(value.id, 48),
    label: text(value.label, 100),
    category: value.category,
    count: count(value.count),
    samples: count(value.samples),
    score: count(value.score, 100),
    detail: text(value.detail, 800),
    firstEpochMs: Number.isSafeInteger(value.firstEpochMs) && value.firstEpochMs > 0 ? value.firstEpochMs : 0,
    lastEpochMs: Number.isSafeInteger(value.lastEpochMs) && value.lastEpochMs > 0 ? value.lastEpochMs : 0,
  }));
}
