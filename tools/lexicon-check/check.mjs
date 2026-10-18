#!/usr/bin/env node

// Validates every lexicon under lexicons/ with the AT Protocol reference implementation,
// checks LogDate's own conventions, validates the example records against their schemas, and
// refuses breaking changes to published schemas (see compat.mjs).
import { execFileSync } from "node:child_process";
import { readdir, readFile } from "node:fs/promises";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { jsonToLex, Lexicons, parseLexiconDoc } from "@atproto/lexicon";
import { breakingChanges } from "./compat.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../..");
const lexiconRoot = join(root, "lexicons");
const mappingFile = join(lexiconRoot, "activity-streams.json");
const publishedFile = join(lexiconRoot, "published.json");
const examplesDir = join(here, "examples");
const ownNamespace = "app.logdate.";
const allowedKeys = new Set(["any", "tid", "nsid", "literal:self"]);

const problems = [];
const report = (where, message) => problems.push(`${where}: ${message}`);

async function jsonFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true });
  const nested = await Promise.all(
    entries.map((entry) => {
      const path = join(dir, entry.name);
      if (entry.isDirectory()) return jsonFiles(path);
      return entry.name.endsWith(".json") ? [path] : [];
    }),
  );
  return nested.flat();
}

const readJson = async (path) => JSON.parse(await readFile(path, "utf8"));

const lexicons = new Lexicons();
const ownRecords = new Set();

for (const path of (await jsonFiles(lexiconRoot)).filter((p) => p !== mappingFile && p !== publishedFile).sort()) {
  const where = relative(root, path);
  const doc = await readJson(path);

  const expectedId = relative(lexiconRoot, path).replace(/\.json$/, "").split(sep).join(".");
  if (doc.id !== expectedId) report(where, `id ${doc.id} doesn't match its path (expected ${expectedId})`);

  try {
    lexicons.add(parseLexiconDoc(doc));
  } catch (error) {
    const issues = error.issues?.map((issue) => `${issue.path.join(".")}: ${issue.message}`);
    report(where, issues?.join("; ") ?? error.message);
    continue;
  }

  if (!doc.id.startsWith(ownNamespace)) continue;

  if (!doc.description) report(where, "every LogDate lexicon needs a top-level description");
  for (const [name, def] of Object.entries(doc.defs)) {
    if (["object", "record", "token", "permission-set"].includes(def.type) && !def.description && !def.title) {
      report(where, `#${name} needs a description`);
    }
  }

  const main = doc.defs.main;
  if (main?.type === "record") {
    ownRecords.add(doc.id);
    if (!allowedKeys.has(main.key)) report(where, `record key "${main.key}" isn't one LogDate uses`);
  }
}

function* references(node) {
  if (Array.isArray(node)) {
    for (const child of node) yield* references(child);
  } else if (node && typeof node === "object") {
    if (node.type === "ref") yield node.ref;
    if (node.type === "union") yield* node.refs;
    for (const child of Object.values(node)) yield* references(child);
  }
}

for (const doc of lexicons) {
  if (!doc.id.startsWith(ownNamespace)) continue;
  for (const ref of references(doc.defs)) {
    const target = ref.startsWith("#") ? `${doc.id}${ref}` : ref;
    if (!lexicons.getDef(target)) report(doc.id, `reference ${ref} doesn't resolve`);
  }
}

const mapping = await readJson(mappingFile);
for (const nsid of ownRecords) {
  if (!(nsid in mapping)) report(relative(root, mappingFile), `${nsid} has no Activity Streams mapping (use null for "none")`);
}
for (const nsid of Object.keys(mapping)) {
  if (!ownRecords.has(nsid)) report(relative(root, mappingFile), `${nsid} isn't a LogDate record type`);
}

for (const path of (await jsonFiles(examplesDir)).sort()) {
  const where = relative(root, path);
  const { collection, record } = await readJson(path);
  try {
    lexicons.assertValidRecord(collection, jsonToLex(record));
  } catch (error) {
    report(where, error.message);
  }
}

// Published schemas may only change compatibly. Each is compared with its version where this
// branch left main (or LEXICON_BASE_REF). A schema that wasn't published there is new, and may be
// anything.
const { published } = await readJson(publishedFile);
const lexiconPath = (nsid) => `lexicons/${nsid.split(".").join("/")}.json`;
const git = (...args) => execFileSync("git", args, { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
let baseRef = process.env.LEXICON_BASE_REF;
try {
  baseRef ??= git("merge-base", "HEAD", "origin/main").trim();
} catch {
  const message = "can't find where this branch left origin/main; fetch it, or set LEXICON_BASE_REF";
  if (published.length > 0) report(relative(root, publishedFile), message);
  else console.warn(`Skipped comparing published schemas with main: ${message}.`);
}
const basePublished = new Set();
if (baseRef) {
  try {
    JSON.parse(git("show", `${baseRef}:lexicons/published.json`)).published.forEach((id) => basePublished.add(id));
  } catch {
    // The list didn't exist yet at the base, so nothing was published there.
  }
}
for (const nsid of published) {
  const where = lexiconPath(nsid);
  if (!lexicons.getDef(nsid) || !nsid.startsWith(ownNamespace)) {
    report(relative(root, publishedFile), `${nsid} isn't a LogDate schema in this folder`);
    continue;
  }
  if (!basePublished.has(nsid)) continue;
  const before = JSON.parse(git("show", `${baseRef}:${where}`));
  const after = await readJson(join(root, where));
  for (const change of breakingChanges(before, after)) {
    report(where, `${change} (published schemas can only change compatibly; see docs/reference/lexicon-style.md)`);
  }
}
for (const nsid of basePublished) {
  if (!published.includes(nsid)) report(relative(root, publishedFile), `${nsid} was published, and can't be unpublished`);
}

if (problems.length > 0) {
  console.error(`Lexicon check failed with ${problems.length} problem(s):`);
  for (const problem of problems) console.error(`  - ${problem}`);
  process.exit(1);
}
console.log(
  `Lexicon check passed: ${ownRecords.size} LogDate record types, all mapped and valid; ` +
    `${published.length} published schema(s) checked for breaking changes.`,
);
