// Tests for the conformance generator's building blocks. Run with `node --test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { jsonToLex, Lexicons, parseLexiconDoc } from "@atproto/lexicon";
import { classify, failureKinds } from "./conformance/failures.mjs";
import { keySchedule, padding } from "./conformance/sealed.mjs";
import { Synthesizer } from "./conformance/synthesize.mjs";

// A schema that uses every constraint LogDate's own schemas don't use yet, so the failure kinds
// stay tested before a real schema needs them.
const sample = {
  lexicon: 1,
  id: "com.example.conformance.sample",
  defs: {
    main: {
      type: "record",
      key: "tid",
      record: {
        type: "object",
        required: ["title", "count", "mode"],
        properties: {
          title: { type: "string", minLength: 2, maxLength: 8, maxGraphemes: 6 },
          when: { type: "string", format: "datetime" },
          count: { type: "integer", minimum: 1, maximum: 5 },
          mode: { type: "string", enum: ["fast", "slow"] },
          fixed: { type: "string", const: "fixed" },
          picks: { type: "array", items: { type: "string" }, minLength: 1, maxLength: 2 },
          choice: { type: "union", refs: ["#a"], closed: true },
          parts: { type: "array", items: { type: "union", refs: ["#a"] } },
          inner: { type: "ref", ref: "#a" },
        },
      },
    },
    a: { type: "object", required: ["name"], properties: { name: { type: "string" } } },
  },
};

const docs = new Map([[sample.id, sample]]);
const lexicons = new Lexicons();
lexicons.add(parseLexiconDoc(structuredClone(sample)));

function rejection(record) {
  try {
    lexicons.assertValidRecord(sample.id, jsonToLex(record));
    return null;
  } catch (error) {
    return error.message;
  }
}

test("a synthesized record is valid", () => {
  const synthesizer = new Synthesizer(docs);
  assert.equal(rejection(synthesizer.minimal(sample.id)), null);
  assert.equal(rejection(synthesizer.full(sample.id)), null);
});

test("every invalid case fails the way its label says", () => {
  const cases = new Synthesizer(docs).invalidCases(sample.id);
  assert.ok(cases.length > 0);
  for (const { name, record, expected } of cases) {
    const message = rejection(record);
    assert.ok(message, `${name} should be rejected`);
    assert.deepEqual(classify(message), expected, `${name}: ${message}`);
  }
});

test("the invalid cases name every kind of failure", () => {
  const kinds = new Set(new Synthesizer(docs).invalidCases(sample.id).map(({ expected }) => expected.kind));
  assert.deepEqual([...kinds].sort(), [...failureKinds].sort());
});

test("messages of every shape are classified", () => {
  const expected = {
    'Record must have the property "a"': { kind: "missing-required", path: "/a" },
    'Record/x/0 must have the property "b"': { kind: "missing-required", path: "/x/0/b" },
    "Record/x must be a string": { kind: "wrong-type", path: "/x" },
    "Record/x should be a blob ref": { kind: "wrong-type", path: "/x" },
    "Record/x must be a valid did": { kind: "invalid-format", path: "/x" },
    "Record/x must be an valid atproto datetime (both RFC-3339 and ISO-8601)": { kind: "invalid-format", path: "/x" },
    "Record/x must not be larger than 2 bytes": { kind: "too-long", path: "/x" },
    "Record/x must not be smaller than 2 bytes": { kind: "too-short", path: "/x" },
    "Record/x can not be less than 1": { kind: "out-of-range", path: "/x" },
    "Record/x must be one of (a|b)": { kind: "not-allowed-value", path: "/x" },
    "Record/x must be fixed": { kind: "not-allowed-value", path: "/x" },
    "Invalid $type: must be lex:a.b.c, got a.b.d": { kind: "wrong-record-type", path: "/$type" },
  };
  for (const [message, want] of Object.entries(expected)) assert.deepEqual(classify(message), want, message);
  assert.throws(() => classify("Something else went wrong"), /Unrecognised/);
});

test("the key schedule and padding reproduce the values the Kotlin tests first pinned", () => {
  const [first] = keySchedule().vectors;
  assert.equal(first.contentKey, "a468cefb3a8ef131555d994017ef9cb4c62a87db9bf1303cc16e62be0db765d5");
  assert.equal(first.keyId, "rjbotc7k4vxiy");
  assert.equal(first.sealedRkeys[0].sealedRkey, "n4mi5n4etheqzgucpsng6icwjf");
  const sizes = Object.fromEntries(padding().sizes.map(({ payloadLength, paddedLength }) => [payloadLength, paddedLength]));
  assert.equal(sizes[508], 512);
  assert.equal(sizes[509], 1024);
  assert.equal(sizes[65533], 131072);
});
