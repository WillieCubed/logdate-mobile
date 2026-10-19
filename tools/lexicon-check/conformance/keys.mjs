// Valid and invalid record keys for each key rule a LogDate record type can use, checked against
// the AT Protocol reference implementation's syntax rules.
import { isValidRecordKey, isValidTid } from "@atproto/syntax";

const rules = {
  any: {
    check: isValidRecordKey,
    valid: ["3jzfcijpj2z2a", "self", "0f8b8a2e-4b8e-4f7e-9d7a-2c1b5d6e7f80", "lake-house", "a.b-c_d:e~f"],
    invalid: ["", ".", "..", "a/b", "has space", "a".repeat(513)],
  },
  tid: {
    check: isValidTid,
    valid: ["3jzfcijpj2z2a", "2222222222222"],
    invalid: ["self", "3jzfcijpj2z2", "3JZFCIJPJ2Z2A", "zzzzzzzzzzzzz"],
  },
  "literal:self": {
    check: (key) => key === "self",
    valid: ["self"],
    invalid: ["", "Self", "other"],
  },
};

/**
 * The keys a record of this type may and may not be stored under, for a key rule from the lexicon.
 * A sample the reference implementation judges differently is reported in `problems`.
 */
export function recordKeyCases(rule, problems, collection) {
  const entry = rules[rule];
  if (!entry) throw new Error(`${collection}: no record key samples for the rule "${rule}"`);
  for (const key of entry.valid) if (!entry.check(key)) problems.push(`${collection}: "${key}" should be a valid ${rule} key`);
  for (const key of entry.invalid) if (entry.check(key)) problems.push(`${collection}: "${key}" should be an invalid ${rule} key`);
  return { valid: entry.valid, invalid: entry.invalid };
}
