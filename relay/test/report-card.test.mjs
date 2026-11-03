import test from "node:test";
import assert from "node:assert/strict";
import { card } from "../public/report-card.js";

class Element {
  constructor(tag) {
    this.tagName = tag;
    this.children = [];
    this.dataset = {};
    this.attributes = {};
    this._text = "";
  }
  set textContent(value) { this._text = String(value); this.children = []; }
  get textContent() { return this._text + this.children.map((child) => child.textContent).join(" "); }
  get childElementCount() { return this.children.length; }
  set innerHTML(value) { throw new Error("Untrusted HTML rendering is not allowed"); }
  appendChild(child) { this.children.push(child); return child; }
  append(...children) { this.children.push(...children); }
  setAttribute(key, value) { this.attributes[key] = value; }
  addEventListener() {}
}

globalThis.document = { createElement: (tag) => new Element(tag) };

function descendants(element) {
  return [element, ...element.children.flatMap(descendants)];
}

test("mixed report cards visibly label behavioral evidence and its limitation", () => {
  const node = card({ id: "sample", player: "Player", server: "test.net", flags: { attack: 3 },
    externalFindings: [
      { category: "BEHAVIORAL", label: "Shield raise response", samples: 24 },
      { category: "OBSERVED_INPUT", label: "Windows mouse", samples: 3 },
    ] });
  assert.match(node.textContent, /Automated Input/);
  assert.match(node.textContent, /Behavioral Evidence/);
  assert.match(node.textContent, /Not definitive proof of cheating/);
  assert.match(node.textContent, /legitimate tools/);
  assert.match(node.textContent, /24 samples/);
  assert.match(node.textContent, /3 events/);
});

test("long names remain available as text and titles without HTML injection", () => {
  const name = "<script>" + "very long name ".repeat(40);
  const node = card({ id: "a/b", player: name, server: "long-server.example.net", flags: {} });
  const all = descendants(node);
  const player = all.find((element) => element.className === "name");
  assert.equal(player.textContent, name);
  assert.equal(player.title, name);
  assert.equal(all.find((element) => element.className === "open").href, "/report/a%2Fb");
});

test("legacy reports render without external metadata", () => {
  const node = card({ player: "Legacy", flags: { use: 1 }, sources: [{ type: "MOD", modName: "Example", count: 1 }] });
  assert.match(node.textContent, /Automated Input/);
  assert.match(node.textContent, /Source context/);
  assert.doesNotMatch(node.textContent, /Behavioral Evidence/);
});
