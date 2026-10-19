// Turns the reference validator's error messages into the language-neutral failures the
// conformance fixtures label invalid records with.

/**
 * Every kind of failure a fixture can name. A client's validator doesn't have to word its errors
 * like the reference implementation; it has to report the same kind at the same path.
 */
export const failureKinds = [
  "missing-required",
  "wrong-type",
  "too-long",
  "too-short",
  "out-of-range",
  "not-allowed-value",
  "invalid-format",
  "wrong-record-type",
  "union-member-not-allowed",
  "union-member-missing-type",
];

const formatTails =
  /(a uri|an valid atproto datetime.*|a valid (at-uri|did|handle|did or a handle|nsid|TID|Record Key)|a cid string|a well-formed BCP 47 language tag)$/;
const typeTails = /(a CID|a boolean|a byte array|a string|an array|an integer|an object|a number)$/;

const rules = [
  [/^Invalid \$type: must be /, "wrong-record-type"],
  [/ must have the property "/, "missing-required"],
  [/ \$type must be one of /, "union-member-not-allowed"],
  [/ must be an object which includes the "\$type" property$/, "union-member-missing-type"],
  [/ must not be (longer|larger) than \d+ (characters|graphemes|bytes)$/, "too-long"],
  [/ must not have more than \d+ elements$/, "too-long"],
  [/ must not be (shorter|smaller) than \d+ (characters|graphemes|bytes)$/, "too-short"],
  [/ must not have fewer than \d+ elements$/, "too-short"],
  [/ can not be (less|greater) than -?\d+$/, "out-of-range"],
  [/ should be a blob ref$/, "wrong-type"],
  [new RegExp(` must be ${formatTails.source}`), "invalid-format"],
  [new RegExp(` must be ${typeTails.source}`), "wrong-type"],
  [/ must be one of \(/, "not-allowed-value"],
  [/ must be \S/, "not-allowed-value"],
];

/**
 * Classifies the error the reference implementation raised for a record, as `{ kind, path }` with
 * the path a JSON Pointer such as `/blocks/0/text`.
 */
export function classify(message) {
  const location = /^(?:Record((?:\/\S+)?)|Invalid \$type)/.exec(message);
  if (!location) throw new Error(`Unrecognised validation error: ${message}`);
  const basePath = location[1] ?? "";
  for (const [pattern, kind] of rules) {
    if (!pattern.test(message)) continue;
    if (kind === "wrong-record-type") return { kind, path: "/$type" };
    if (kind === "missing-required") return { kind, path: `${basePath}/${/property "(.+)"$/.exec(message)[1]}` };
    return { kind, path: basePath };
  }
  throw new Error(`Unrecognised validation error: ${message}`);
}
