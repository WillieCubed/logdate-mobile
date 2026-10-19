import { strict as assert } from "node:assert";
import { createHash } from "node:crypto";
import { test } from "node:test";
import { recordKeyProblems } from "./keys.mjs";

const entry = "3a1c2b4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";
const ref = (collection, rkey) => ({ collection, rkey });

test("a transcript is keyed by its entry and audio block", () => {
  const record = { entry: ref("app.logdate.journal.entry", entry), blockId: "b4" };
  assert.deepEqual(recordKeyProblems("app.logdate.journal.transcript", "any", `${entry}:b4`, record), []);
  assert.match(recordKeyProblems("app.logdate.journal.transcript", "any", entry, record)[0], /should be ".*:b4"/);
});

test("a membership is keyed by its journal and entry", () => {
  const record = { journal: ref("app.logdate.journal.journal", "j1"), entry: ref("app.logdate.journal.entry", "e1") };
  assert.deepEqual(recordKeyProblems("app.logdate.journal.membership", "any", "j1:e1", record), []);
  assert.equal(recordKeyProblems("app.logdate.journal.membership", "any", "e1:j1", record).length, 1);
});

test("an entry link is keyed by its entry and the SHA-256 of its evidence id", () => {
  const evidenceId = "derived:journey:owner:device:a:b";
  const hash = createHash("sha256").update(evidenceId).digest("hex");
  const record = { entry: ref("app.logdate.journal.entry", entry), evidenceId };
  assert.deepEqual(recordKeyProblems("app.logdate.place.entryLink", "any", `${entry}:${hash}`, record), []);
  assert.equal(recordKeyProblems("app.logdate.place.entryLink", "any", `${entry}:${evidenceId}`, record).length, 1);
});

test("a sample hour is keyed by its UTC hour, device and part", () => {
  const first = { device: "d1", startsAt: "2026-10-19T14:00:00.000Z" };
  assert.deepEqual(recordKeyProblems("app.logdate.place.sample", "any", "2026-10-19T14:d1", first), []);
  assert.deepEqual(recordKeyProblems("app.logdate.place.sample", "any", "2026-10-19T14:d1:3", { ...first, part: 3 }), []);
  assert.equal(recordKeyProblems("app.logdate.place.sample", "any", "2026-10-19T14:d1", { ...first, part: 3 }).length, 1);
});

test("a sample hour's first part leaves part out and starts on the hour", () => {
  const problems = recordKeyProblems("app.logdate.place.sample", "any", "2026-10-19T14:d1", {
    device: "d1",
    startsAt: "2026-10-19T14:00:00.000Z",
    part: 1,
  });
  assert.deepEqual(problems, ["the first part of an hour leaves part out"]);
  const offHour = recordKeyProblems("app.logdate.place.sample", "any", "2026-10-19T14:d1", { device: "d1", startsAt: "2026-10-19T14:30:00.000Z" });
  assert.match(offHour[0], /isn't the start of a UTC hour/);
});

test("keys follow the schema's key rule and the record key syntax", () => {
  assert.deepEqual(recordKeyProblems("app.logdate.actor.profile", "literal:self", "self", {}), []);
  assert.equal(recordKeyProblems("app.logdate.actor.profile", "literal:self", "me", {}).length, 1);
  assert.equal(recordKeyProblems("app.logdate.share.reply", "tid", "not-a-tid", {}).length, 1);
  assert.match(recordKeyProblems("app.logdate.journal.entry", "any", "has space", {})[0], /isn't a valid record key/);
  assert.match(recordKeyProblems("app.logdate.journal.entry", "any", undefined, {})[0], /needs an rkey/);
});
