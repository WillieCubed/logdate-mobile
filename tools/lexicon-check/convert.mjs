// The reference for turning a device's fractional measurement into one of LogDate's integer fields
// (docs/reference/lexicon-style.md, "No floating-point numbers"). The Kotlin and Swift apps must
// produce the same integers; conversion-vectors.json pins the cases they test against.
//
// JavaScript numbers are 64-bit doubles, the arithmetic every client uses: a 32-bit Float from
// Android is widened to a Double first, exactly, and then scaled.

/** Largest integer every client and the JSON form of a record represent exactly: 2^53 − 1. */
export const MAX_SAFE = Number.MAX_SAFE_INTEGER;

/** Rounds `x` to the nearest integer, halves away from zero. Exact: `x − trunc(x)` has no error. */
export function roundHalfAwayFromZero(x) {
  const whole = Math.trunc(x);
  const fraction = x - whole;
  if (fraction >= 0.5) return whole + 1;
  if (fraction <= -0.5) return whole - 1;
  return whole;
}

/** `scaled` as an integer, or null when it's outside the safe integer range once rounded. */
function inRange(scaled) {
  const rounded = roundHalfAwayFromZero(scaled);
  return Math.abs(rounded) > MAX_SAFE ? null : rounded;
}

/**
 * Converts `value` for a field of `kind`, or returns null when the field must be left out:
 *
 * - `centimeters`: meters (or meters per second) times 100, rounded; negative becomes 0.
 * - `bearing`: degrees reduced into [0, 360) with fmod, times 100, rounded; 36000 becomes 0.
 * - `thousandths`: a 0 to 1 confidence times 1000, rounded, then kept between 0 and 1000.
 *
 * NaN, infinity, and finite values too large for the field once scaled give null: they're
 * refused, never saturated to a limit.
 */
export function toIntegerField(kind, value) {
  if (typeof value !== "number" || !Number.isFinite(value)) return null;
  switch (kind) {
    case "centimeters": {
      const result = inRange(value * 100);
      return result === null ? null : Math.max(0, result);
    }
    case "bearing": {
      let degrees = value % 360;
      if (degrees < 0) degrees += 360;
      const result = roundHalfAwayFromZero(degrees * 100);
      return result >= 36000 ? result - 36000 : result;
    }
    case "thousandths": {
      const result = inRange(value * 1000);
      return result === null ? null : Math.min(1000, Math.max(0, result));
    }
    default:
      throw new Error(`unknown field kind ${kind}`);
  }
}
