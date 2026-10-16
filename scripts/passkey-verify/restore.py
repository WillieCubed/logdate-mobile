#!/usr/bin/env python3
"""Signed passive restore acceptance against an explicitly selected server."""

import argparse
import importlib.util
import json
import secrets
from pathlib import Path

import requests
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec

_spec = importlib.util.spec_from_file_location("logdate_passkey_sim", Path(__file__).with_name("sim.py"))
sim = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(sim)


def request(session, base, method, path, *, payload=None, token=None, expected=200):
    headers = {"Authorization": "Bearer " + token} if token else {}
    try:
        response = session.request(
            method, base + path, json=payload, headers=headers,
            timeout=30, allow_redirects=False,
        )
    except requests.RequestException:
        raise sim.VerifierError(f"{method} {path}: request failed") from None
    allowed = (expected,) if isinstance(expected, int) else expected
    if response.status_code not in allowed:
        raise sim.VerifierError(f"{method} {path}: HTTP {response.status_code}, expected {allowed}")
    return response


def data(response):
    try:
        value = response.json()["data"]
        if not isinstance(value, dict):
            raise ValueError
        return value
    except (KeyError, TypeError, ValueError):
        raise sim.VerifierError("Server returned malformed restore data") from None


def registration(key, credential_id, challenge, rp, origin):
    client = sim.build_client_data("webauthn.create", challenge, origin)
    authenticator = sim.build_auth_data(
        rp, 0, include_attested=True,
        credential_id=sim.b64u_decode(credential_id),
        cose_pub=sim.cose_pubkey(key.public_key()),
    )
    attestation = sim.cbor.encode({"fmt": "none", "attStmt": {}, "authData": authenticator})
    return {"id": credential_id, "rawId": credential_id, "type": "public-key", "response": {
        "clientDataJSON": sim.b64u(client), "attestationObject": sim.b64u(attestation),
    }}


def assertion(key, credential_id, challenge, rp, origin, *, invalid=False, sign_count=1):
    client = sim.build_client_data("webauthn.get", challenge, origin)
    # A native restore is passive: neither user presence nor verification is asserted.
    authenticator = sim.sha256(rp.encode()) + bytes([0]) + sign_count.to_bytes(4, "big")
    signature = key.sign(authenticator + sim.sha256(client), ec.ECDSA(hashes.SHA256()))
    if invalid:
        signature = signature[:-1] + bytes([signature[-1] ^ 1])
    return {"challenge": challenge, "credential": {
        "id": credential_id, "rawId": credential_id, "type": "public-key", "response": {
            "clientDataJSON": sim.b64u(client), "authenticatorData": sim.b64u(authenticator),
            "signature": sim.b64u(signature), "userHandle": "",
        },
    }}


def delete_fixture(session, base, token, owner):
    identity = data(request(session, base, "GET", "/api/v1/auth/me", token=token))
    if identity["account"]["id"] != owner:
        raise sim.VerifierError("Cleanup token does not authenticate the disposable account; deletion refused")
    request(session, base, "DELETE", "/api/v1/auth/me", token=token, expected=(200, 204))
    print("Disposable account deleted", flush=True)


def create_fixture(session, base, origin, rp):
    try:
        return sim.signup(session, base, origin, rp, "restore_verify_" + secrets.token_hex(8), "Restore acceptance fixture")
    except sim.SignupCompletedError as error:
        recovered = sim.signin(session, base, origin, rp, error.state)
        token = recovered["data"]["tokens"]["accessToken"]
        owner = sim.account_id_from_user_handle(error.state["user_handle"])
        delete_fixture(session, base, token, owner)
        raise sim.VerifierError("Signup completion was invalid; disposable account was recovered and deleted") from None


def run_acceptance(session, base, origin, rp, expected_release=None):
    sim.require_https_origin(base, "server")
    sim.require_https_origin(origin, "client origin")
    sim.require_rp_id_for_origin(rp, origin)
    if expected_release:
        health = request(session, base, "GET", "/health").json()
        if health.get("release") != expected_release or health.get("status") != "healthy":
            raise sim.VerifierError("Selected server is not running the expected healthy release")
        print("Verified active release:", expected_release, flush=True)
    state = create_fixture(session, base, origin, rp)
    token = state["auth"]["data"]["tokens"]["accessToken"]
    owner = state["auth"]["data"]["account"]["id"]
    endpoint = "/api/v1/auth/restore/"
    try:
        key = ec.generate_private_key(ec.SECP256R1())
        credential_id = sim.b64u(secrets.token_bytes(32))
        options = data(request(session, base, "POST", endpoint + "register/begin", token=token))
        if options.get("rpId") != rp:
            raise sim.VerifierError("Restore registration RP ID differs from the selected contract")
        credential = registration(key, credential_id, options["challenge"], rp, origin)
        request(session, base, "POST", endpoint + "register/complete", token=token,
                payload={"credentialJson": json.dumps(credential), "challenge": options["challenge"]})
        print("Signed restore registration passed", flush=True)

        def begin():
            options = data(request(session, base, "POST", endpoint + "begin"))
            if options.get("rpId") != rp or options.get("userVerification") != "discouraged":
                raise sim.VerifierError("Restore options do not match the passive authentication contract")
            return options["challenge"]

        for label, kwargs in [("invalid signature", {"invalid": True}),
                              ("foreign origin", {"origin": "https://foreign.example"})]:
            challenge = begin()
            client_origin = kwargs.pop("origin", origin)
            payload = assertion(key, credential_id, challenge, rp, client_origin, **kwargs)
            request(session, base, "POST", endpoint + "complete", payload=payload, expected=401)
            print("Rejected", label, flush=True)
        challenge = begin()
        payload = assertion(key, credential_id, challenge, rp, origin, sign_count=0)
        restored = data(request(session, base, "POST", endpoint + "complete", payload=payload))
        if restored["account"]["id"] != owner:
            raise sim.VerifierError("Restore authenticated a different account")
        restored_token = restored["tokens"]["accessToken"]
        identity = data(request(session, base, "GET", "/api/v1/auth/me", token=restored_token))
        if identity["account"]["id"] != owner:
            raise sim.VerifierError("Restore token authenticates a different account")
        print("Passive assertion restored the correct account", flush=True)
        request(session, base, "POST", endpoint + "complete", payload=payload, expected=401)
        payload = assertion(key, credential_id, begin(), rp, origin, sign_count=0)
        retried = data(request(session, base, "POST", endpoint + "complete", payload=payload))
        if retried["account"]["id"] != owner:
            raise sim.VerifierError("Restore retry authenticated a different account")
        identity = data(request(session, base, "GET", "/api/v1/auth/me", token=retried["tokens"]["accessToken"]))
        if identity["account"]["id"] != owner:
            raise sim.VerifierError("Restore retry token authenticates a different account")
        for counter, expected in [(1, 200), (1, 401), (2, 200)]:
            payload = assertion(key, credential_id, begin(), rp, origin, sign_count=counter)
            response = request(session, base, "POST", endpoint + "complete", payload=payload, expected=expected)
            if expected == 200:
                result = data(response)
                if result["account"]["id"] != owner:
                    raise sim.VerifierError("Counter advancement authenticated a different account")
                identity = data(request(session, base, "GET", "/api/v1/auth/me", token=result["tokens"]["accessToken"]))
                if identity["account"]["id"] != owner:
                    raise sim.VerifierError("Counter advancement token authenticates a different account")
        print("Rejected exact replay and stale counters; accepted fresh zero and advancing counter assertions", flush=True)
    finally:
        delete_fixture(session, base, token, owner)
    if expected_release:
        health = request(session, base, "GET", "/health").json()
        if health.get("release") != expected_release or health.get("status") != "healthy":
            raise sim.VerifierError("Server release or health changed during restore acceptance")
    print("PASSIVE RESTORE ACCEPTANCE PASSED", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True)
    parser.add_argument("--origin", required=True)
    parser.add_argument("--expected-rp-id", required=True)
    parser.add_argument("--expected-release", required=True)
    args = parser.parse_args()
    try:
        with requests.Session() as session:
            run_acceptance(session, args.base, args.origin, args.expected_rp_id, args.expected_release)
    except sim.VerifierError as error:
        # HTTP helpers report only status and path; response bodies and tokens stay private.
        print("Passive restore acceptance failed:", error)
        return 1
    except (KeyError, TypeError, ValueError):
        print("Passive restore acceptance failed: server returned malformed contract data.")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
