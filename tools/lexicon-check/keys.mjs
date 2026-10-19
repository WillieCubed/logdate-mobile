// Checks an example record's key against its schema's key rule and against the key formats
// LogDate documents for records that two devices may both write (docs/reference/lexicons.md).
// A key built from the record's own fields means the same fact is always the same record.
import { createHash } from "node:crypto";
import { isValidRecordKey, isValidTid } from "@atproto/syntax";

const sha256Hex = (text) => createHash("sha256").update(text, "utf8").digest("hex");

/** The UTC hour `startsAt` names, as YYYY-MM-DDTHH, or null when it isn't exactly on an hour. */
function utcHour(startsAt) {
  const time = new Date(startsAt);
  if (Number.isNaN(time.getTime())) return null;
  if (time.getUTCMinutes() !== 0 || time.getUTCSeconds() !== 0 || time.getUTCMilliseconds() !== 0) return null;
  return time.toISOString().slice(0, 13);
}

/** The key LogDate's documented format gives `record`, or undefined when its collection has none. */
function expectedKey(collection, record, report) {
  switch (collection) {
    case "app.logdate.journal.transcript":
      return `${record.entry?.rkey}:${record.blockId}`;
    case "app.logdate.journal.membership":
      return `${record.journal?.rkey}:${record.entry?.rkey}`;
    case "app.logdate.place.entryLink":
      return `${record.entry?.rkey}:${sha256Hex(String(record.evidenceId))}`;
    case "app.logdate.place.sample": {
      const hour = utcHour(record.startsAt);
      if (hour === null) {
        report(`startsAt ${record.startsAt} isn't the start of a UTC hour`);
        return undefined;
      }
      if (record.part === 1) report("the first part of an hour leaves part out");
      const part = record.part > 1 ? `:${record.part}` : "";
      return `${hour}:${record.device}${part}`;
    }
    default:
      return undefined;
  }
}

/**
 * Returns one message per way `rkey` breaks the key rule `keyRule` (a record schema's `key`) or
 * the format LogDate documents for `collection`.
 */
export function recordKeyProblems(collection, keyRule, rkey, record) {
  const problems = [];
  const report = (message) => problems.push(message);

  if (typeof rkey !== "string") {
    report("an example needs an rkey, the record key it would be stored under");
    return problems;
  }
  if (!isValidRecordKey(rkey)) {
    report(`rkey "${rkey}" isn't a valid record key`);
    return problems;
  }
  if (keyRule === "tid" && !isValidTid(rkey)) report(`rkey "${rkey}" isn't a TID, which ${collection} keys must be`);
  if (keyRule?.startsWith("literal:") && rkey !== keyRule.slice("literal:".length)) {
    report(`rkey "${rkey}" must be "${keyRule.slice("literal:".length)}"`);
  }

  const expected = expectedKey(collection, record, report);
  if (expected !== undefined && rkey !== expected) report(`rkey "${rkey}" should be "${expected}", built from the record's fields`);
  return problems;
}
