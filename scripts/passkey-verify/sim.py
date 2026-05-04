#!/usr/bin/env python3
"""LogDate passkey end-to-end verifier.

Simulates a WebAuthn authenticator (EC P-256, 'none' attestation) against a
live LogDate server, exercising the full signup -> signin loop. Proves that
the production WebAuthn config (RP ID + origin) is wired up correctly.

Usage: python sim.py [--base https://cloud.logdate.app] [--origin ...]

This is a TEST CLIENT, not a real authenticator. It does the same crypto a
real platform authenticator would do (random P-256 keypair, valid CBOR
attestation, real ECDSA signatures), but the user verification is a lie.
"""

import argparse
import hashlib
import json
import secrets
import sys
import time
from base64 import urlsafe_b64encode, urlsafe_b64decode

import requests
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from fido2 import cbor


def b64u(b: bytes) -> str:
    return urlsafe_b64encode(b).rstrip(b"=").decode()


def b64u_decode(s: str) -> bytes:
    pad = "=" * (-len(s) % 4)
    return urlsafe_b64decode(s + pad)


def sha256(b: bytes) -> bytes:
    return hashlib.sha256(b).digest()


def cose_pubkey(pubkey: ec.EllipticCurvePublicKey) -> bytes:
    """COSE_Key for EC P-256 / ES256 (RFC 8152). Map keys are integers."""
    nums = pubkey.public_numbers()
    x = nums.x.to_bytes(32, "big")
    y = nums.y.to_bytes(32, "big")
    cose = {1: 2, 3: -7, -1: 1, -2: x, -3: y}
    return cbor.encode(cose)


def build_auth_data(
    rp_id: str,
    sign_count: int,
    *,
    include_attested: bool,
    credential_id: bytes = b"",
    cose_pub: bytes = b"",
) -> bytes:
    """authenticatorData per WebAuthn §6.1."""
    rp_id_hash = sha256(rp_id.encode())
    flags = 0x01 | 0x04  # UP + UV
    if include_attested:
        flags |= 0x40  # AT
    out = rp_id_hash + bytes([flags]) + sign_count.to_bytes(4, "big")
    if include_attested:
        aaguid = b"\x00" * 16
        cid_len = len(credential_id).to_bytes(2, "big")
        out += aaguid + cid_len + credential_id + cose_pub
    return out


def build_client_data(action: str, challenge: str, origin: str) -> bytes:
    """clientDataJSON. `challenge` is the same base64url string the server sent."""
    return json.dumps(
        {
            "type": action,
            "challenge": challenge,
            "origin": origin,
            "crossOrigin": False,
        },
        separators=(",", ":"),
    ).encode()


def post(url: str, payload: dict) -> requests.Response:
    r = requests.post(url, json=payload, timeout=30)
    return r


def health(base_url: str) -> None:
    r = requests.get(f"{base_url}/health", timeout=15)
    print(f"GET {base_url}/health -> {r.status_code} {r.text[:200]}")
    if not r.ok:
        sys.exit(f"health check failed; aborting passkey verification")


def signup(base_url: str, origin: str, username: str, display_name: str) -> dict:
    print(f"\n=== SIGNUP {username} ===")
    r = post(
        f"{base_url}/api/v1/auth/signup/passkey/begin",
        {"username": username, "displayName": display_name},
    )
    print(f"begin -> {r.status_code}")
    if not r.ok:
        sys.exit(f"signup/begin failed: {r.status_code} {r.text}")
    body = r.json()
    session_token = body["data"]["sessionToken"]
    opts = body["data"]["registrationOptions"]
    challenge, rp_id = opts["challenge"], opts["rpId"]
    user_id = opts["user"]["id"]
    print(f"  rpId={rp_id}  user.id={user_id}  challenge.len={len(challenge)}")

    private_key = ec.generate_private_key(ec.SECP256R1())
    cose_pub = cose_pubkey(private_key.public_key())
    credential_id = secrets.token_bytes(32)

    client_data = build_client_data("webauthn.create", challenge, origin)
    auth_data = build_auth_data(
        rp_id,
        sign_count=0,
        include_attested=True,
        credential_id=credential_id,
        cose_pub=cose_pub,
    )
    attestation_object = cbor.encode(
        {"fmt": "none", "attStmt": {}, "authData": auth_data}
    )

    cred_id_b64 = b64u(credential_id)
    r = post(
        f"{base_url}/api/v1/auth/signup/passkey/complete",
        {
            "sessionToken": session_token,
            "credential": {
                "id": cred_id_b64,
                "rawId": cred_id_b64,
                "response": {
                    "clientDataJSON": b64u(client_data),
                    "attestationObject": b64u(attestation_object),
                },
                "type": "public-key",
            },
        },
    )
    print(f"complete -> {r.status_code}")
    if not r.ok:
        sys.exit(f"signup/complete failed: {r.status_code} {r.text}")
    print(f"  body keys: {list(r.json().get('data', {}).keys())}")
    return {
        "credential_id": credential_id,
        "private_key": private_key,
        "user_id": user_id,
        "username": username,
        "auth": r.json(),
    }


def signin(base_url: str, origin: str, state: dict, sign_count: int = 1) -> dict:
    print(f"\n=== SIGNIN {state['username']} ===")
    r = post(
        f"{base_url}/api/v1/auth/signin/passkey/begin",
        {"username": state["username"]},
    )
    print(f"begin -> {r.status_code}")
    if not r.ok:
        sys.exit(f"signin/begin failed: {r.status_code} {r.text}")
    body = r.json()
    challenge = body["data"]["challenge"]
    rp_id = body["data"]["rpId"]
    allow = body["data"]["allowCredentials"]
    print(f"  rpId={rp_id}  allowCreds={len(allow)}  challenge.len={len(challenge)}")

    client_data = build_client_data("webauthn.get", challenge, origin)
    auth_data = build_auth_data(rp_id, sign_count=sign_count, include_attested=False)
    signature = state["private_key"].sign(
        auth_data + sha256(client_data),
        ec.ECDSA(hashes.SHA256()),
    )

    cred_id_b64 = b64u(state["credential_id"])
    r = post(
        f"{base_url}/api/v1/auth/signin/passkey/complete",
        {
            "challenge": challenge,
            "credential": {
                "id": cred_id_b64,
                "rawId": cred_id_b64,
                "response": {
                    "clientDataJSON": b64u(client_data),
                    "authenticatorData": b64u(auth_data),
                    "signature": b64u(signature),
                    "userHandle": state["user_id"],
                },
                "type": "public-key",
            },
        },
    )
    print(f"complete -> {r.status_code}")
    if not r.ok:
        sys.exit(f"signin/complete failed: {r.status_code} {r.text}")
    auth = r.json()
    tokens = auth.get("data", {}).get("tokens") or auth.get("data", {})
    print(f"  body data keys: {list(auth.get('data', {}).keys())}")
    return auth


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--base", default="https://cloud.logdate.app")
    p.add_argument("--origin", default=None,
                   help="Origin in clientDataJSON; defaults to --base")
    p.add_argument("--username", default=f"verify_{int(time.time())}")
    p.add_argument("--display-name", default="Deploy Verifier")
    args = p.parse_args()
    origin = args.origin or args.base

    print(f"base={args.base}  origin={origin}  username={args.username}")
    health(args.base)
    state = signup(args.base, origin, args.username, args.display_name)
    auth = signin(args.base, origin, state)
    print("\n=== END-TO-END PASSKEY VERIFICATION SUCCEEDED ===")


if __name__ == "__main__":
    main()
