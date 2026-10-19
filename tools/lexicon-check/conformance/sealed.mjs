// Known-answer vectors for the personal space's sealed records: the key schedule, the opaque
// record key, payload padding, and whole `app.logdate.crypto.sealed` envelopes. Built with
// node:crypto and the reference DAG-CBOR encoder, independently of the Kotlin implementation.
import { createCipheriv, createHash, createHmac, hkdfSync } from "node:crypto";
import { base32, dagCbor, hex } from "./encoding.mjs";

const SALT = "app.logdate.space.personal";
const CONTENT_INFO = "sealed-record v1";
const INDEX_INFO = "sealed-rkey v1";
const SEALED_RKEY_LENGTH = 26;
const KEY_ID_BYTES = 8;
const TAG = 16;
const HEADER = 4;
const SMALLEST_BUCKET = 512;
const STEP = 65536;
const MAX_CIPHERTEXT = 4_000_000;
const INLINE_LIMIT = 4096;
const VERSION = 1;

const sha256 = (bytes) => createHash("sha256").update(bytes).digest();
const sequence = (start, length) => Buffer.from(Array.from({ length }, (_, i) => start + i));

function keysFor(identityKey) {
  const derive = (info) => Buffer.from(hkdfSync("sha256", identityKey, SALT, info, 32));
  const contentKey = derive(CONTENT_INFO);
  const indexKey = derive(INDEX_INFO);
  const keyId = base32(createHmac("sha256", contentKey).update("keyId").digest().subarray(0, KEY_ID_BYTES));
  return { contentKey, indexKey, keyId };
}

const sealedRkey = (indexKey, collection, rkey) =>
  base32(createHmac("sha256", indexKey).update(`${collection}/${rkey}`).digest()).slice(0, SEALED_RKEY_LENGTH);

/** Payload length to padded size: powers of two from 512 bytes to 64 KiB, then multiples of 64 KiB. */
function paddedSize(length) {
  const needed = length + HEADER;
  if (needed > STEP) return Math.ceil(needed / STEP) * STEP;
  let size = SMALLEST_BUCKET;
  while (size < needed) size *= 2;
  return size;
}

function pad(payload) {
  const padded = Buffer.alloc(paddedSize(payload.length));
  padded.writeUInt32BE(payload.length, 0);
  payload.copy(padded, HEADER);
  return padded;
}

const additionalData = (space, author, rkey) => `app.logdate.crypto.sealed|${VERSION}|${space}|${author}|${rkey}`;

function encrypt(contentKey, nonce, padded, aad) {
  const cipher = createCipheriv("aes-256-gcm", contentKey, nonce);
  cipher.setAAD(Buffer.from(aad));
  return Buffer.concat([cipher.update(padded), cipher.final(), cipher.getAuthTag()]);
}

/** The bytes as `{ length, sha256 }`, plus the hex when it's short enough to read in a fixture. */
function digest(bytes) {
  const entry = { length: bytes.length, sha256: hex(sha256(bytes)) };
  if (bytes.length <= INLINE_LIMIT) entry.hex = hex(bytes);
  return entry;
}

const keyScheduleCases = [
  { name: "sequential-identity-key", identityKey: sequence(0, 32) },
  { name: "second-identity-key", identityKey: sequence(0x80, 32) },
];

const sealedRkeyInputs = [
  ["app.logdate.journal.entry", "3k2a"],
  ["app.logdate.journal.entry", "3k2b"],
  ["app.logdate.journal.journal", "0f8b8a2e-4b8e-4f7e-9d7a-2c1b5d6e7f80"],
  ["app.logdate.place.place", "lake-house"],
  ["app.logdate.actor.settings", "self"],
];

export function keySchedule() {
  return {
    description:
      "How the personal space's keys derive from the account's 32-byte identity key. Every client must reproduce these values exactly.",
    algorithm: {
      contentKey: `HKDF-SHA256(ikm = identity key, salt = "${SALT}", info = "${CONTENT_INFO}"), 32 bytes`,
      indexKey: `HKDF-SHA256(ikm = identity key, salt = "${SALT}", info = "${INDEX_INFO}"), 32 bytes`,
      keyId: `base32(HMAC-SHA256(content key, "keyId")[0 until ${KEY_ID_BYTES}])`,
      sealedRkey: `base32(HMAC-SHA256(index key, "{collection}/{rkey}")).take(${SEALED_RKEY_LENGTH})`,
      base32: "RFC 4648, lowercase, without padding",
    },
    vectors: keyScheduleCases.map(({ name, identityKey }) => {
      const keys = keysFor(identityKey);
      return {
        name,
        identityKey: hex(identityKey),
        contentKey: hex(keys.contentKey),
        indexKey: hex(keys.indexKey),
        keyId: keys.keyId,
        sealedRkeys: sealedRkeyInputs.map(([collection, rkey]) => ({
          collection,
          rkey,
          sealedRkey: sealedRkey(keys.indexKey, collection, rkey),
        })),
      };
    }),
    refusedIdentityKeyLengths: [0, 16, 31, 33, 64],
  };
}

const paddingLengths = [
  0, 1, 507, 508, 509, 1020, 1021, 2044, 2045, 4092, 4093, 8188, 8189, 16380, 16381, 32764, 32765, 65531, 65532, 65533, 131068, 131069,
  3_997_692,
];

export function padding() {
  const largestSealable = Math.floor((MAX_CIPHERTEXT - TAG) / STEP) * STEP - HEADER;
  return {
    description:
      "How a payload is padded before encryption: a 4-byte big-endian length, the payload, then zeros up to a bucket. Buckets are powers of two from 512 bytes to 64 KiB, then whole multiples of 64 KiB.",
    sizes: paddingLengths.map((payloadLength) => ({ payloadLength, paddedLength: paddedSize(payloadLength) })),
    largestSealablePayload: largestSealable,
    smallestRefusedPayload: largestSealable + 1,
  };
}

const author = "did:plc:owner";
const space = `at://${author}/space/${SALT}/self`;
const phrase = "Pancakes at the lake. ";

function envelopeCases() {
  const rawLink = `b${base32(Buffer.concat([Buffer.from([0x01, 0x71, 0x12, 0x20]), sha256("Pancakes at the lake")]))}`;
  return [
    {
      name: "small-record",
      description: "A small record in the smallest bucket.",
      identityKey: sequence(0, 32),
      nonce: sequence(0xa0, 12),
      payload: {
        collection: "app.logdate.journal.entry",
        rkey: "3k2a",
        record: { $type: "app.logdate.journal.entry", text: "Pancakes at the lake" },
      },
      expand: [],
    },
    {
      name: "every-kind-of-value",
      description:
        "A record with positive, negative and 64-bit integers, an array, nested objects, bytes and a CID link, whose text is long enough that the payload passes 64 KiB and pads to a multiple of 64 KiB.",
      identityKey: sequence(0, 32),
      nonce: sequence(0xb0, 12),
      payload: {
        collection: "app.logdate.journal.entry",
        rkey: "3k2b",
        record: {
          $type: "app.logdate.journal.entry",
          mood: -3,
          steps: 12345,
          createdMs: 5_000_000_000_123,
          tags: ["lake", "breakfast", 7],
          place: { name: "The lake", visits: { count: 2, lastYear: 2025 } },
          thumbnail: { $bytes: sequence(1, 9).toString("base64") },
          photo: { $link: rawLink },
          text: "",
        },
      },
      expand: [{ path: "/record/text", repeat: phrase, times: 3200 }],
    },
  ];
}

function expanded(payload, expand) {
  const copy = structuredClone(payload);
  for (const { path, repeat, times } of expand) {
    const keys = path.split("/").slice(1);
    const last = keys.pop();
    keys.reduce((node, key) => node[key], copy)[last] = repeat.repeat(times);
  }
  return copy;
}

function flipped(bytes, index) {
  const copy = Buffer.from(bytes);
  copy[index] ^= 1;
  return copy;
}

export function envelopes() {
  return {
    description:
      "Whole sealed envelopes. Sealing the payload with the given nonce must produce exactly this envelope, and opening the envelope must produce exactly this payload. A payload with an `expand` entry is written compactly: replace the string at `path` with `repeat` written `times` times before encoding.",
    algorithm: {
      payload: "DAG-CBOR of { collection, rkey, record }",
      padding: "see padding.json",
      cipher: "AES-256-GCM under the content key and the nonce; the 16-byte tag is appended to the ciphertext",
      additionalData: `"app.logdate.crypto.sealed|${VERSION}|{space}|{author}|{sealedRkey}"`,
    },
    envelopes: envelopeCases().map((entry) => {
      const keys = keysFor(entry.identityKey);
      const payload = expanded(entry.payload, entry.expand);
      const rkey = sealedRkey(keys.indexKey, payload.collection, payload.rkey);
      const encoded = Buffer.from(dagCbor(payload));
      const padded = pad(encoded);
      const aad = additionalData(space, author, rkey);
      const ciphertext = encrypt(keys.contentKey, entry.nonce, padded, aad);
      const result = {
        name: entry.name,
        description: entry.description,
        identityKey: hex(entry.identityKey),
        author,
        space,
        nonce: hex(entry.nonce),
        payload: entry.payload,
        expand: entry.expand,
        keyId: keys.keyId,
        sealedRkey: rkey,
        dagCbor: digest(encoded),
        paddedLength: padded.length,
        additionalData: aad,
        ciphertext: digest(ciphertext),
      };
      if (ciphertext.length <= INLINE_LIMIT) {
        result.envelope = {
          $type: "app.logdate.crypto.sealed",
          version: VERSION,
          keyId: keys.keyId,
          nonce: { $bytes: Buffer.from(entry.nonce).toString("base64") },
          ciphertext: { $bytes: ciphertext.toString("base64") },
        };
        result.refusedEnvelopes = refusals(entry, keys, rkey, ciphertext);
      }
      return result;
    }),
  };
}

/** Changes that must make an envelope fail to open, each as the replacement for one input. */
function refusals(entry, keys, rkey, ciphertext) {
  const b64 = (bytes) => ({ $bytes: Buffer.from(bytes).toString("base64") });
  return [
    { name: "opened for another author", author: "did:plc:else" },
    { name: "opened in another space", space: `at://${author}/space/${SALT}/other` },
    { name: "stored under another sealed key", sealedRkey: `${rkey.slice(0, -1)}${rkey.endsWith("a") ? "b" : "a"}` },
    { name: "envelope version 2", envelope: { version: 2 } },
    { name: "sealed with another key", envelope: { keyId: "aaaaaaaaaaaaa" } },
    { name: "nonce of 11 bytes", envelope: { nonce: b64(entry.nonce.subarray(0, 11)) } },
    { name: "flipped nonce bit", envelope: { nonce: b64(flipped(entry.nonce, 0)) } },
    { name: "flipped ciphertext bit", envelope: { ciphertext: b64(flipped(ciphertext, 100)) } },
    { name: "flipped authentication tag bit", envelope: { ciphertext: b64(flipped(ciphertext, ciphertext.length - 1)) } },
    { name: "ciphertext that is not a sealed size", envelope: { ciphertext: b64(ciphertext.subarray(0, ciphertext.length - 1)) } },
  ];
}
