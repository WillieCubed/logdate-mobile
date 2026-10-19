import { strict as assert } from "node:assert";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { roundHalfAwayFromZero, toIntegerField } from "./convert.mjs";

const vectors = JSON.parse(readFileSync(new URL("./conversion-vectors.json", import.meta.url), "utf8"));
const special = { NaN: NaN, Infinity: Infinity, "-Infinity": -Infinity };
const number = (input) => (typeof input === "string" ? special[input] : input);

test("halves round away from zero, exactly", () => {
  for (const { input, expected } of vectors.roundHalfAwayFromZero) {
    assert.equal(roundHalfAwayFromZero(input), expected, `round(${input})`);
  }
});

test("every conversion vector gives its expected integer, or null to leave the field out", () => {
  for (const { kind, input, expected } of vectors.fields) {
    assert.equal(toIntegerField(kind, number(input)), expected, `${kind}(${input})`);
  }
});

test("the float32 vectors are the exact double values of those floats", () => {
  for (const { input, note } of vectors.fields) {
    const float = note?.match(/^([0-9.]+)f widened/)?.[1];
    if (float) assert.equal(Math.fround(Number(float)), input, `${float}f`);
  }
});

test("bearings stay in range for any finite input, and are never refused for size", () => {
  for (const degrees of [-1e300, -721.37, -360, -0, 0, 179.995, 359.995, 1e17, 1e300]) {
    const value = toIntegerField("bearing", degrees);
    assert.ok(Number.isInteger(value) && value >= 0 && value <= 35999, `bearing(${degrees}) = ${value}`);
  }
});
