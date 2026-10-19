// DAG-CBOR bytes and CIDs for conformance fixtures, from the AT Protocol reference implementation.
import { cidForLex, encode } from "@atproto/lex-cbor";
import { jsonToLex } from "@atproto/lex-json";

export const hex = (bytes) => Buffer.from(bytes).toString("hex");

/** The deterministic DAG-CBOR bytes of a record written in the JSON form of the AT Protocol data model. */
export const dagCbor = (record) => encode(jsonToLex(record));

/** The CID of a record: CIDv1, dag-cbor, SHA-256. */
export const recordCid = async (record) => (await cidForLex(jsonToLex(record))).toString();

const alphabet = "abcdefghijklmnopqrstuvwxyz234567";

/** RFC 4648 base32, lowercase, without padding. */
export function base32(bytes) {
  let bits = 0;
  let value = 0;
  let out = "";
  for (const byte of bytes) {
    value = (value << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      out += alphabet[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += alphabet[(value << (5 - bits)) & 31];
  return out;
}
