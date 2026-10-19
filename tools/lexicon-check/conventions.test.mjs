import { strict as assert } from "node:assert";
import { test } from "node:test";
import { optionalCreatedAtProblems } from "./conventions.mjs";

const record = (required, createdAt) => ({
  defs: { main: { type: "record", key: "any", record: { type: "object", required, properties: { createdAt } } } },
});
const datetime = { type: "string", format: "datetime" };

test("an optional createdAt needs a description", () => {
  assert.deepEqual(optionalCreatedAtProblems(record([], datetime)), ["createdAt is optional, so its description must say when it's absent"]);
  assert.equal(optionalCreatedAtProblems(record([], { ...datetime, description: "  " })).length, 1);
  assert.deepEqual(optionalCreatedAtProblems(record([], { ...datetime, description: "When it was made, when known." })), []);
});

test("a required createdAt, a record without one, and other definitions pass", () => {
  assert.deepEqual(optionalCreatedAtProblems(record(["createdAt"], datetime)), []);
  assert.deepEqual(optionalCreatedAtProblems({ defs: { main: { type: "record", record: { properties: {} } } } }), []);
  assert.deepEqual(optionalCreatedAtProblems({ defs: { main: { type: "object", properties: { createdAt: datetime } } } }), []);
});
