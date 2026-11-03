import test from "node:test";
import assert from "node:assert/strict";
import { upgradeEvidenceReport } from "../netlify/lib/report-evidence.mjs";

const old = '<html><head></head><body><main><section><h2>Session</h2>keep</section><section><h2>External input and Behavioral Evidence</h2><table><tr><td>old layout</td></tr></table></section><section><h2>Mods</h2>keep mods</section></main></body></html>';
const data = {
  externalCoverage: "Hooks installed",
  behaviorScore: 25,
  externalFindings: [
    { id: "windows-0", label: "Windows mouse", category: "OBSERVED_INPUT", count: 12, samples: 12 },
    { id: "shield-response", label: "Axe swaps aligned with shield raises", category: "BEHAVIORAL", count: 1, samples: 24, detail: "Mean 100 ms, SD 0.2 ms" },
  ],
};

test("saved report evidence is separated without changing other sections", () => {
  const result = upgradeEvidenceReport(old, JSON.stringify(data));
  assert.match(result, /<h2>External Input<\/h2>/);
  assert.match(result, /<h2>Behavioral Evidence<\/h2>/);
  assert.match(result, /Not definitive proof of cheating/);
  assert.match(result, /Mouse buttons/);
  assert.match(result, /24 samples/);
  assert.match(result, /View measurements/);
  assert.match(result, /<h2>Session<\/h2>keep/);
  assert.match(result, /<h2>Mods<\/h2>keep mods/);
  assert.doesNotMatch(result, /old layout/);
  assert.match(result, /href="\/report-evidence.css"/);
  assert.equal(upgradeEvidenceReport(result, data), result);
});

test("evidence text is escaped and invalid metadata leaves saved reports intact", () => {
  const hostile = { ...data, externalCoverage: '<img src=x onerror="bad()">',
    externalFindings: [{ id: "aim", category: "BEHAVIORAL", label: "<script>bad()</script>", detail: "<img>", samples: 600 }] };
  const result = upgradeEvidenceReport(old, hostile);
  assert.match(result, /&lt;script&gt;/);
  assert.doesNotMatch(result, /<script>bad/);
  assert.doesNotMatch(result, /<img/);
  assert.equal(upgradeEvidenceReport(old, "invalid json"), old);
  assert.equal(upgradeEvidenceReport(old, null), old);
});

test("empty evidence has clear separate states without a table or false verdict", () => {
  const result = upgradeEvidenceReport(old, { externalFindings: [] });
  assert.match(result, /No Windows input observations recorded/);
  assert.match(result, /No behavioral findings recorded/);
  assert.match(result, /not a probability of cheating/);
  assert.doesNotMatch(result, /<table/);
});
