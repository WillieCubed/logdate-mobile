// Reads the repository's lexicons for the conformance generator.
import { readdir, readFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { Lexicons, parseLexiconDoc } from "@atproto/lexicon";

const here = dirname(fileURLToPath(import.meta.url));
export const root = resolve(here, "../../..");
export const conformanceRoot = join(root, "conformance");
export const examplesDir = join(root, "tools/lexicon-check/examples");
const lexiconRoot = join(root, "lexicons");
const notSchemas = new Set([join(lexiconRoot, "activity-streams.json"), join(lexiconRoot, "published.json")]);

export async function jsonFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const nested = await Promise.all(
    entries.map((entry) => {
      const path = join(dir, entry.name);
      if (entry.isDirectory()) return jsonFiles(path);
      return entry.name.endsWith(".json") ? [path] : [];
    }),
  );
  return nested.flat().sort();
}

export const readJson = async (path) => JSON.parse(await readFile(path, "utf8"));

/** Every lexicon document by id, and the reference implementation's registry holding them. */
export async function loadLexicons() {
  const docs = new Map();
  const lexicons = new Lexicons();
  for (const path of (await jsonFiles(lexiconRoot)).filter((p) => !notSchemas.has(p))) {
    const doc = await readJson(path);
    docs.set(doc.id, doc);
    lexicons.add(parseLexiconDoc(structuredClone(doc)));
  }
  return { docs, lexicons };
}

/** The ids of LogDate's own record types, in a stable order. */
export function logdateRecordTypes(docs) {
  return [...docs.values()]
    .filter((doc) => doc.id.startsWith("app.logdate.") && doc.defs.main?.type === "record")
    .map((doc) => doc.id)
    .sort();
}
