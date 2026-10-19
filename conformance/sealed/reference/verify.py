#!/usr/bin/env python3
"""Recomputes every sealed-record fixture from scratch and compares it with the fixture files.

This is a second, independent implementation of the personal space's sealing: it builds the key
schedule, the opaque record keys, the padding, the DAG-CBOR payload and the AES-GCM envelope
itself, with only the `cryptography` package (`pip install cryptography`). If it prints
"Sealed fixtures verified" the fixtures describe what the format really is, whichever client's
code produced them first.

    python3 conformance/sealed/reference/verify.py
"""
import base64
import hashlib
import hmac
import json
import sys
from pathlib import Path

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

SEALED = Path(__file__).resolve().parent.parent
SALT = b"app.logdate.space.personal"
problems = []


def check(label, actual, expected):
    if actual != expected:
        problems.append(f"{label}: expected {expected!r}, computed {actual!r}")


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


verify_key_schedule()
verify_padding()
verify_envelopes()

if problems:
    print(f"{len(problems)} fixture value(s) differ:", file=sys.stderr)
    for problem in problems:
        print(f"  - {problem}", file=sys.stderr)
    sys.exit(1)
print("Sealed fixtures verified.")
