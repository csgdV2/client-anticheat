import { getStore } from "@netlify/blobs";
import { upgradeEvidenceReport } from "../lib/report-evidence.mjs";

export const config = { path: "/report/:id" };

const idSafe = (v) =>
  (typeof v === "string" ? v : "").replace(/[^A-Za-z0-9._-]/g, "").slice(0, 128);

export default async (req, context) => {
  const id = idSafe(context.params?.id);
  if (!id) return new Response("Not found", { status: 404 });

  const store = getStore({ name: "combat-tracker", consistency: "strong" });
  const html = await store.get(`report/${id}`, { type: "text" });
  if (!html) return new Response("Report not found", { status: 404 });

  let displayed = html;
  if (html.includes("External input and Behavioral Evidence")) {
    try {
      const canonical = await store.get(`canonical/${id}`, { type: "text" });
      if (canonical) displayed = upgradeEvidenceReport(html, canonical);
      if (displayed === html) {
        const index = await store.get("index", { type: "json" });
        const metadata = Array.isArray(index) ? index.find((entry) => entry?.id === id) : null;
        if (metadata) displayed = upgradeEvidenceReport(html, metadata);
      }
    } catch {
      displayed = html;
    }
  }

  return new Response(displayed, {
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "public, max-age=60",
    },
  });
};
