import test from "node:test";
import assert from "node:assert/strict";
import { sanitizeExternalFindings } from "../netlify/lib/evidence.mjs";
import { filterReports, hasBehavioral, hasWindowsInput, BEHAVIOR_WARNING } from "../public/report-data.js";

test("external evidence keeps input observations separate from behavioral suspicion", () => {
  const findings = sanitizeExternalFindings([
    { id: "click", category: "BEHAVIORAL", samples: 24, score: 25 },
    { id: "windows", category: "OBSERVED_INPUT", count: 3, score: 0 },
    { id: "bad", category: "PROVEN_CHEATER", score: 100 }, null,
  ]);
  assert.equal(findings.length, 2);
  assert.equal(findings[0].category, "BEHAVIORAL");
  assert.equal(findings[1].category, "OBSERVED_INPUT");
  assert.match(BEHAVIOR_WARNING, /not definitive proof of cheating/i);
});

test("external report metadata is bounded and rejects invalid timestamps", () => {
  const findings = sanitizeExternalFindings(Array.from({ length: 100 }, () => ({
    category: "BEHAVIORAL", label: "x".repeat(200), detail: "y".repeat(2000),
    samples: 1e15, count: -10, score: 1000, firstEpochMs: "bad", lastEpochMs: Infinity,
  })));
  assert.equal(findings.length, 12);
  assert.equal(findings[0].label.length, 100);
  assert.equal(findings[0].detail.length, 800);
  assert.equal(findings[0].samples, 1_000_000);
  assert.equal(findings[0].score, 100);
  assert.equal(findings[0].count, 0);
  assert.equal(findings[0].lastEpochMs, 0);
  assert.deepEqual(sanitizeExternalFindings(null), []);
});

test("removed clicking findings are hidden while stored source data remains intact", () => {
  const report = { flags: {}, externalFindings: [
    { id: "click-regularity", category: "BEHAVIORAL", samples: 192 },
    { id: "click-pattern", category: "BEHAVIORAL", samples: 192 },
  ] };
  assert.deepEqual(sanitizeExternalFindings(report.externalFindings), []);
  assert.equal(hasBehavioral(report), false);
  assert.equal(report.externalFindings.length, 2);
});

const reports = [
  { id: "old", player: "Alice", server: "example.net", endUtc: "2026-11-01 12:00:00 UTC", flags: { attack: 3 } },
  { id: "behavior", player: "Bob", server: "test.net", endUtc: "2026-11-03 12:00:00 UTC",
    externalFindings: [{ category: "BEHAVIORAL", label: "Shield raise response" }] },
  { id: "native", player: "Alice", server: "test.net", endUtc: "2026-11-02 12:00:00 UTC",
    externalFindings: [{ category: "OBSERVED_INPUT", label: "Windows mouse" }] },
];

test("list filters support old reports and external only reports", () => {
  assert.deepEqual(filterReports(reports, { evidence: "behavioral" }).map((r) => r.id), ["behavior"]);
  assert.deepEqual(filterReports(reports, { evidence: "windows" }).map((r) => r.id), ["native"]);
  assert.deepEqual(filterReports(reports, { evidence: "automated" }).map((r) => r.id), ["old"]);
  assert.equal(hasBehavioral(reports[0]), false);
  assert.equal(hasWindowsInput(reports[2]), true);
});

test("list searches server and finding names and sorts without mutating source", () => {
  assert.equal(filterReports(reports, { query: "test.net" }).length, 2);
  assert.equal(filterReports(reports, { query: "shield raise response" })[0].id, "behavior");
  assert.deepEqual(filterReports(reports).map((r) => r.id), ["behavior", "native", "old"]);
  assert.deepEqual(filterReports(reports, { sort: "oldest" }).map((r) => r.id), ["old", "native", "behavior"]);
  assert.equal(reports[0].id, "old");
});
