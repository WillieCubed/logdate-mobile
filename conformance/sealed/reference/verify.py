#!/usr/bin/env python3
"""Recomputes the conformance fixtures from scratch and compares them with the fixture files.

This is a second, independent implementation, in Python, of two things the Node generator and the
Kotlin tests also do:

- Sealing: the key schedule, the opaque record keys, the padding, the DAG-CBOR payload and the
  AES-GCM envelope, and opening an envelope, including refusing every change the fixtures list.
- Record encoding: the DAG-CBOR bytes and the CID of every valid record in conformance/records/,
  and the digest of the lexicon files the fixtures were generated from.

It needs only the `cryptography` package (`pip install cryptography`). If it prints "Conformance
fixtures verified" the fixtures describe what the formats really are, whichever client's code
produced them first.

    python3 conformance/sealed/reference/verify.py
"""
import base64
import hashlib
import hmac
import json
import sys
from pathlib import Path

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

SEALED = Path(__file__).resolve().parent.parent
CONFORMANCE = SEALED.parent
REPOSITORY = CONFORMANCE.parent
SUPPORTED_VERSION = 1
SALT = b"app.logdate.space.personal"
problems = []


def check(label, actual, expected):
    if actual != expected:
        problems.append(f"{label}: expected {expected!r}, computed {actual!r}")


def check_files(label, actual, expected):
    """Compares two {path: sha256} maps and names each file that is missing, extra or different."""
    for path in sorted(set(actual) | set(expected)):
        if path not in expected:
            problems.append(f"{label}: {path} is not in the manifest")
        elif path not in actual:
            problems.append(f"{label}: {path} is in the manifest but not in this checkout")
        elif actual[path] != expected[path]:
            problems.append(f"{label}: {path} differs from the manifest (this checkout {actual[path][:12]}, manifest {expected[path][:12]})")


def b32(raw):
    return base64.b32encode(raw).decode().lower().rstrip("=")


def b32decode(text):
    return base64.b32decode(text.upper() + "=" * (-len(text) % 8))


def hkdf(ikm, info):
    return HKDF(hashes.SHA256(), 32, SALT, info.encode()).derive(ikm)


def keys_for(identity):
    content = hkdf(identity, "sealed-record v1")
    index = hkdf(identity, "sealed-rkey v1")
    key_id = b32(hmac.new(content, b"keyId", hashlib.sha256).digest()[:8])
    return content, index, key_id


def sealed_rkey(index_key, collection, rkey):
    return b32(hmac.new(index_key, f"{collection}/{rkey}".encode(), hashlib.sha256).digest())[:26]


def padded_size(length):
    needed = length + 4
    if needed > 65536:
        return -(-needed // 65536) * 65536
    size = 512
    while size < needed:
        size *= 2
    return size


class Link(str):
    """A CID link: tag 42 over 0x00 followed by the binary CID."""


def cbor_head(major, n):
    if n < 24:
        return bytes([major << 5 | n])
    for info, width in ((24, 1), (25, 2), (26, 4), (27, 8)):
        if n < 1 << (8 * width):
            return bytes([major << 5 | info]) + n.to_bytes(width, "big")
    raise ValueError("integer too large")


def dag_cbor(value):
    """Deterministic DAG-CBOR: map keys sorted by length, then bytes; no floats."""
    if isinstance(value, bool):
        return bytes([0xF5 if value else 0xF4])
    if isinstance(value, int):
        return cbor_head(0, value) if value >= 0 else cbor_head(1, -1 - value)
    if isinstance(value, Link):
        return bytes([0xD8, 42]) + dag_cbor(b"\x00" + b32decode(value[1:]))
    if isinstance(value, str):
        raw = value.encode()
        return cbor_head(3, len(raw)) + raw
    if isinstance(value, bytes):
        return cbor_head(2, len(value)) + value
    if isinstance(value, list):
        return cbor_head(4, len(value)) + b"".join(dag_cbor(v) for v in value)
    keys = sorted(value, key=lambda k: (len(k.encode()), k.encode()))
    return cbor_head(5, len(keys)) + b"".join(dag_cbor(k) + dag_cbor(value[k]) for k in keys)


def from_json(value):
    """The JSON form of the AT Protocol data model, as the Python values dag_cbor writes."""
    if isinstance(value, list):
        return [from_json(v) for v in value]
    if isinstance(value, dict):
        if set(value) == {"$bytes"}:
            return base64.b64decode(value["$bytes"] + "=" * (-len(value["$bytes"]) % 4))
        if set(value) == {"$link"}:
            return Link(value["$link"])
        return {k: from_json(v) for k, v in value.items()}
    return value


def cid_of(encoded):
    """CIDv1, dag-cbor (0x71), SHA-256, written in lowercase base32 with the 'b' prefix."""
    return "b" + b32(bytes([0x01, 0x71, 0x12, 0x20]) + hashlib.sha256(encoded).digest())


def is_padded_size(size):
    if not 512 <= size <= 4_000_000 - 16:
        return False
    return size & (size - 1) == 0 if size <= 65536 else size % 65536 == 0


def open_envelope(identity, space, author, rkey, envelope):
    """Opens a stored envelope and returns its DAG-CBOR payload. Raises when it must be refused."""
    content, _, key_id = keys_for(identity)
    if envelope["version"] != 1:
        raise ValueError("unsupported envelope version")
    if envelope["keyId"] != key_id:
        raise ValueError("sealed with another key")
    nonce = base64.b64decode(envelope["nonce"]["$bytes"])
    ciphertext = base64.b64decode(envelope["ciphertext"]["$bytes"])
    if len(nonce) != 12:
        raise ValueError("nonce is not 12 bytes")
    if not is_padded_size(len(ciphertext) - 16):
        raise ValueError("ciphertext is not a sealed size")
    aad = f"app.logdate.crypto.sealed|1|{space}|{author}|{rkey}".encode()
    plaintext = AESGCM(content).decrypt(nonce, ciphertext, aad)  # raises InvalidTag when anything differs
    length = int.from_bytes(plaintext[:4], "big")
    if length > len(plaintext) - 4 or padded_size(length) != len(plaintext):
        raise ValueError("padding length is invalid")
    if any(plaintext[4 + length :]):
        raise ValueError("padding is not zeros")
    return plaintext[4 : 4 + length]


def check_bytes(label, entry, data):
    check(f"{label} length", len(data), entry["length"])
    check(f"{label} sha256", hashlib.sha256(data).hexdigest(), entry["sha256"])
    if "hex" in entry:
        check(f"{label} hex", data.hex(), entry["hex"])


def verify_key_schedule():
    schedule = json.loads((SEALED / "key-schedule.json").read_text())
    for vector in schedule["vectors"]:
        label = f"key schedule {vector['name']}"
        content, index, key_id = keys_for(bytes.fromhex(vector["identityKey"]))
        check(f"{label} content key", content.hex(), vector["contentKey"])
        check(f"{label} index key", index.hex(), vector["indexKey"])
        check(f"{label} key id", key_id, vector["keyId"])
        for entry in vector["sealedRkeys"]:
            check(
                f"{label} sealed rkey of {entry['collection']}/{entry['rkey']}",
                sealed_rkey(index, entry["collection"], entry["rkey"]),
                entry["sealedRkey"],
            )


def verify_padding():
    padding = json.loads((SEALED / "padding.json").read_text())
    for entry in padding["sizes"]:
        check(f"padded size of {entry['payloadLength']}", padded_size(entry["payloadLength"]), entry["paddedLength"])
    largest = (4_000_000 - 16) // 65536 * 65536 - 4
    check("largest sealable payload", largest, padding["largestSealablePayload"])
    check("smallest refused payload", largest + 1, padding["smallestRefusedPayload"])


def expanded(payload, expand):
    for rule in expand:
        *parents, last = rule["path"].split("/")[1:]
        node = payload
        for key in parents:
            node = node[key]
        node[last] = rule["repeat"] * rule["times"]
    return payload


def verify_envelopes():
    for entry in json.loads((SEALED / "envelopes.json").read_text())["envelopes"]:
        label = f"envelope {entry['name']}"
        content, index, key_id = keys_for(bytes.fromhex(entry["identityKey"]))
        payload = expanded(json.loads(json.dumps(entry["payload"])), entry["expand"])
        rkey = sealed_rkey(index, payload["collection"], payload["rkey"])
        check(f"{label} key id", key_id, entry["keyId"])
        check(f"{label} sealed rkey", rkey, entry["sealedRkey"])

        encoded = dag_cbor(from_json(payload))
        check_bytes(f"{label} DAG-CBOR", entry["dagCbor"], encoded)
        size = padded_size(len(encoded))
        check(f"{label} padded length", size, entry["paddedLength"])
        padded = len(encoded).to_bytes(4, "big") + encoded + bytes(size - 4 - len(encoded))

        aad = f"app.logdate.crypto.sealed|1|{entry['space']}|{entry['author']}|{rkey}"
        check(f"{label} additional data", aad, entry["additionalData"])
        ciphertext = AESGCM(content).encrypt(bytes.fromhex(entry["nonce"]), padded, aad.encode())
        check_bytes(f"{label} ciphertext", entry["ciphertext"], ciphertext)

        if "envelope" in entry:
            envelope = entry["envelope"]
            check(f"{label} envelope ciphertext", base64.b64decode(envelope["ciphertext"]["$bytes"]), ciphertext)
            check(f"{label} envelope nonce", base64.b64decode(envelope["nonce"]["$bytes"]), bytes.fromhex(entry["nonce"]))
            check(f"{label} envelope key id", envelope["keyId"], key_id)
            identity = bytes.fromhex(entry["identityKey"])
            opened = open_envelope(identity, entry["space"], entry["author"], rkey, envelope)
            check(f"{label} opened payload", opened, encoded)
            verify_refusals(label, entry, identity, rkey)


def verify_refusals(label, entry, identity, rkey):
    """Every listed change must make the envelope fail to open."""
    refusals = entry["refusedEnvelopes"]
    if not refusals:
        problems.append(f"{label}: has an envelope but no refusals")
    for refusal in refusals:
        try:
            open_envelope(
                bytes.fromhex(refusal.get("identityKey", entry["identityKey"])),
                refusal.get("space", entry["space"]),
                refusal.get("author", entry["author"]),
                refusal.get("sealedRkey", rkey),
                {**entry["envelope"], **refusal.get("envelope", {})},
            )
        except (ValueError, InvalidTag):
            continue
        problems.append(f"{label}: the envelope opened although {refusal['name']}")


def verify_records(manifest):
    """Encodes every valid record to DAG-CBOR and a CID with this script's own encoder."""
    record_files = [name for name in manifest["files"] if name.startswith("records/")]
    check("record files in the manifest", sorted(record_files), sorted(f"records/{t}.json" for t in manifest["recordTypes"]))
    cases = 0
    for name in record_files:
        suite = json.loads((CONFORMANCE / name).read_text())
        for case in suite["valid"]:
            encoded = dag_cbor(from_json(case["record"]))
            check(f"{name} {case['name']} DAG-CBOR", encoded.hex(), case["dagCbor"])
            check(f"{name} {case['name']} CID", cid_of(encoded), case["cid"])
            cases += 1
    return cases


def verify_inputs(manifest):
    """Recomputes the digest of the lexicon and example files, when they are next to the fixtures."""
    lexicons = REPOSITORY / "lexicons"
    if not lexicons.is_dir():
        return
    entries = sorted(
        (path.relative_to(REPOSITORY).as_posix(), hashlib.sha256(path.read_bytes()).hexdigest())
        for folder in ("lexicons/app/logdate", "lexicons/community", "tools/lexicon-check/examples")
        for path in (REPOSITORY / folder).rglob("*.json")
    )
    check_files("input files", dict(entries), manifest["inputs"]["files"])
    listing = "".join(f"{sha}  {name}\n" for name, sha in entries)
    check("input digest", hashlib.sha256(listing.encode()).hexdigest(), manifest["inputs"]["digest"])


manifest = json.loads((CONFORMANCE / "manifest.json").read_text())
check("suite version", manifest["version"], SUPPORTED_VERSION)
verify_key_schedule()
verify_padding()
verify_envelopes()
record_cases = verify_records(manifest)
verify_inputs(manifest)

if problems:
    print(f"{len(problems)} fixture value(s) differ:", file=sys.stderr)
    for problem in problems:
        print(f"  - {problem}", file=sys.stderr)
    sys.exit(1)
print(f"Conformance fixtures verified: sealed envelopes and {record_cases} valid records.")
