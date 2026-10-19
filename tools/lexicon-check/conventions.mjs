// LogDate's own writing rules for record schemas (docs/reference/lexicon-style.md) that the AT
// Protocol reference implementation doesn't check.

/**
 * Returns a message when a record's `createdAt` is optional without a description. The style guide
 * makes `createdAt` required unless existing data has no time for it, and then the field's
 * description must say when it's absent, so readers know the gap is expected.
 */
export function optionalCreatedAtProblems(doc) {
  const main = doc.defs?.main;
  if (main?.type !== "record") return [];
  const createdAt = main.record?.properties?.createdAt;
  if (!createdAt || (main.record.required ?? []).includes("createdAt")) return [];
  if (typeof createdAt.description === "string" && createdAt.description.trim() !== "") return [];
  return ["createdAt is optional, so its description must say when it's absent"];
}
