#!/usr/bin/env python3
"""Sign test accounts up on a LogDate server running on this machine.

The identity publishing rehearsal needs accounts that went through the real passkey sign-up, so
their identities are created by the same server code that created every production identity.
This plays the part of a passkey authenticator: it makes a fresh P-256 key for each account and
answers the server's registration challenge with a "none" attestation, which the server verifies
in full. It uses only the Python standard library.

It refuses any server that is not on a loopback address, because the accounts it makes are throwaway
test accounts with keys nobody keeps.
"""

import argparse
import base64
import hashlib
import ipaddress
import json
import secrets
import sys
import urllib.error
import urllib.request
import uuid
from urllib.parse import urlsplit

# NIST P-256 (secp256r1) domain parameters.
P = 0xFFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF
A = P - 3
B = 0x5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B
N = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
G = (
    0x6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296,
    0x4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5,
)


class RehearsalError(Exception):
    """A sign-up the rehearsal can't continue past."""


def point_add(first, second):
    if first is None:
        return second
    if second is None:
        return first
    (x1, y1), (x2, y2) = first, second
    if x1 == x2 and (y1 + y2) % P == 0:
        return None
    if first == second:
        slope = (3 * x1 * x1 + A) * pow(2 * y1, -1, P) % P
    else:
        slope = (y2 - y1) * pow(x2 - x1, -1, P) % P
    x3 = (slope * slope - x1 - x2) % P
    return x3, (slope * (x1 - x3) - y1) % P


def scalar_multiply(scalar, point=G):
    result = None
    addend = point
    while scalar:
        if scalar & 1:
            result = point_add(result, addend)
        addend = point_add(addend, addend)
        scalar >>= 1
    return result


def on_curve(point):
    x, y = point
    return (y * y - (x * x * x + A * x + B)) % P == 0


def new_public_key():
    """A fresh P-256 public key. The private half is discarded: the rehearsal never signs in."""
    return scalar_multiply(secrets.randbelow(N - 1) + 1)


def cbor(value):
    """Encodes the CBOR subset WebAuthn attestation needs (RFC 8949)."""
    if isinstance(value, bool):
        return bytes([0xF5 if value else 0xF4])
    if isinstance(value, int):
        return _cbor_head(0, value) if value >= 0 else _cbor_head(1, -1 - value)
    if isinstance(value, bytes):
        return _cbor_head(2, len(value)) + value
    if isinstance(value, str):
        encoded = value.encode("utf-8")
        return _cbor_head(3, len(encoded)) + encoded
    if isinstance(value, list):
        return _cbor_head(4, len(value)) + b"".join(cbor(item) for item in value)
    if isinstance(value, dict):
        return _cbor_head(5, len(value)) + b"".join(cbor(key) + cbor(item) for key, item in value.items())
    raise TypeError(f"cannot encode {type(value).__name__} as CBOR")


def _cbor_head(major, argument):
    if argument < 24:
        return bytes([(major << 5) | argument])
    for additional, size in ((24, 1), (25, 2), (26, 4), (27, 8)):
        if argument < 1 << (8 * size):
            return bytes([(major << 5) | additional]) + argument.to_bytes(size, "big")
    raise ValueError("CBOR argument too large")


def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def require_loopback(base_url):
    parts = urlsplit(base_url)
    host = parts.hostname or ""
    if parts.scheme not in ("http", "https"):
        raise RehearsalError(f"{base_url} is not an http(s) URL")
    if host == "localhost":
        return
    try:
        if ipaddress.ip_address(host).is_loopback:
            return
    except ValueError:
        pass
    raise RehearsalError(f"refusing to make test accounts on {host}: only loopback servers are allowed")


def registration(challenge, rp_id, origin):
    """clientDataJSON and attestationObject for a "none" attestation over a new key."""
    x, y = new_public_key()
    credential_id = secrets.token_bytes(32)
    cose_key = cbor({1: 2, 3: -7, -1: 1, -2: x.to_bytes(32, "big"), -3: y.to_bytes(32, "big")})
    flags = 0x01 | 0x04 | 0x40  # user present, user verified, attested credential data included
    authenticator_data = (
        hashlib.sha256(rp_id.encode("utf-8")).digest()
        + bytes([flags])
        + (0).to_bytes(4, "big")
        + bytes(16)
        + len(credential_id).to_bytes(2, "big")
        + credential_id
        + cose_key
    )
    client_data = json.dumps(
        {"type": "webauthn.create", "challenge": challenge, "origin": origin, "crossOrigin": False},
        separators=(",", ":"),
    ).encode("utf-8")
    attestation = cbor({"fmt": "none", "attStmt": {}, "authData": authenticator_data})
    return credential_id, client_data, attestation


def post_json(url, payload):
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", "replace")[:500]
        raise RehearsalError(f"POST {urlsplit(url).path} answered {error.code}: {body}") from None


def sign_up(base_url, origin, username):
    status, begin = post_json(
        f"{base_url}/api/v1/auth/signup/passkey/begin",
        {"username": username, "displayName": username, "requestedOwnerId": str(uuid.uuid4())},
    )
    if status != 200:
        raise RehearsalError(f"signup/begin answered {status}")
    data = begin["data"]
    options = data["registrationOptions"]
    credential_id, client_data, attestation = registration(options["challenge"], options["rpId"], origin)
    status, complete = post_json(
        f"{base_url}/api/v1/auth/signup/passkey/complete",
        {
            "sessionToken": data["sessionToken"],
            "credential": {
                "id": b64url(credential_id),
                "rawId": b64url(credential_id),
                "type": "public-key",
                "response": {"clientDataJSON": b64url(client_data), "attestationObject": b64url(attestation)},
            },
        },
    )
    if status != 201:
        raise RehearsalError(f"signup/complete answered {status}")
    account = complete["data"]["account"]
    if not account.get("did") or not account.get("handle"):
        raise RehearsalError(f"sign-up for {username} returned no identity")
    return {"accountId": account["id"], "username": username, "did": account["did"], "handle": account["handle"]}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base", required=True, help="LogDate server base URL on a loopback address")
    parser.add_argument("--origin", required=True, help="WebAuthn origin the server trusts, such as https://localhost")
    parser.add_argument("--prefix", default="rehearsal", help="username prefix")
    parser.add_argument("--count", type=int, default=1)
    parser.add_argument("--output", required=True, help="file the created accounts are written to as JSON")
    args = parser.parse_args(argv)
    base_url = args.base.rstrip("/")
    try:
        require_loopback(base_url)
        if args.count < 1:
            raise RehearsalError("--count must be at least 1")
        accounts = [sign_up(base_url, args.origin, f"{args.prefix}{index}") for index in range(1, args.count + 1)]
    except RehearsalError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    with open(args.output, "w", encoding="utf-8") as output:
        json.dump(accounts, output, indent=2)
    for account in accounts:
        print(f"signed up {account['handle']} -> {account['did']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
