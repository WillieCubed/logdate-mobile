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
import ipaddress
import json
import os
import re
import secrets
import stat
import sys
import time
import uuid
from base64 import urlsafe_b64encode, urlsafe_b64decode
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse
from urllib.parse import urlsplit

import requests
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fido2 import cbor


PRIVATE_FILE_MODE = 0o600
DEPLOY_STATE_FORMAT = "logdate-deploy-smoke-v2"
HOST_LABEL = re.compile(r"^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")


class VerifierError(Exception):
    """A redacted verifier failure that callers can recover from."""


class SignupCompletedError(VerifierError):
    def __init__(self, message: str, state: dict) -> None:
        super().__init__(message)
        self.state = state


def b64u(b: bytes) -> str:
    return urlsafe_b64encode(b).rstrip(b"=").decode()


def b64u_decode(s: str) -> bytes:
    pad = "=" * (-len(s) % 4)
    return urlsafe_b64decode(s + pad)


def decode_registration_user_handle(encoded: str) -> bytes:
    try:
        decoded = b64u_decode(encoded)
        if b64u(decoded) != encoded:
            raise ValueError
        account_id = decoded.decode("ascii")
        if str(uuid.UUID(account_id)) != account_id:
            raise ValueError
    except (ValueError, UnicodeDecodeError):
        raise VerifierError("registration user handle is not a canonical account UUID") from None
    return decoded


def account_id_from_user_handle(user_handle: bytes) -> str:
    try:
        account_id = user_handle.decode("ascii")
        if str(uuid.UUID(account_id)) != account_id:
            raise ValueError
        return account_id
    except (ValueError, UnicodeDecodeError):
        raise VerifierError("credential user handle is not a canonical account UUID") from None


def apk_key_hash_origin_from_fingerprint(fingerprint: str) -> str:
    compact = fingerprint.replace(":", "")
    if not re.fullmatch(r"[0-9A-Fa-f]{64}", compact):
        raise VerifierError("Android certificate fingerprint must contain exactly 32 bytes")
    return f"android:apk-key-hash:{b64u(bytes.fromhex(compact))}"


def apk_key_hash_matches_fingerprint(fingerprint: str, origin: str) -> bool:
    try:
        expected = apk_key_hash_origin_from_fingerprint(fingerprint)
        suffix = origin.removeprefix("android:apk-key-hash:")
        decoded = b64u_decode(suffix)
        return len(decoded) == 32 and b64u(decoded) == suffix and origin == expected
    except (VerifierError, ValueError):
        return False


def sha256(b: bytes) -> bytes:
    return hashlib.sha256(b).digest()


def private_key_to_pem(private_key: ec.EllipticCurvePrivateKey) -> str:
    return private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    ).decode()


def private_key_from_pem(pem: str) -> ec.EllipticCurvePrivateKey:
    private_key = serialization.load_pem_private_key(pem.encode(), password=None)
    if not isinstance(private_key, ec.EllipticCurvePrivateKey):
        raise VerifierError("credential file does not contain an EC private key")
    return private_key


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


def require_private_file(path: Path, label: str, *, allow_empty: bool = False) -> None:
    if path.is_symlink() or not path.is_file():
        raise VerifierError(f"{label} must be a regular file")
    mode = stat.S_IMODE(path.stat().st_mode)
    if mode != PRIVATE_FILE_MODE:
        raise VerifierError(f"{label} must have mode 600")
    if not allow_empty and path.stat().st_size == 0:
        raise VerifierError(f"{label} is empty")


def require_https_origin(value: str, label: str) -> str:
    if any(character.isspace() or ord(character) < 0x20 or ord(character) == 0x7F for character in value):
        raise VerifierError(f"{label} must be an origin-only HTTPS URL")
    try:
        parsed = urlsplit(value)
    except ValueError:
        raise VerifierError(f"{label} must be an origin-only HTTPS URL") from None
    if (
        parsed.scheme != "https"
        or not parsed.netloc
        or parsed.netloc.endswith(":")
        or parsed.username is not None
        or parsed.password is not None
        or parsed.path
        or parsed.query
        or parsed.fragment
    ):
        raise VerifierError(f"{label} must be an origin-only HTTPS URL")
    try:
        host = parsed.hostname
        port = parsed.port
    except ValueError:
        raise VerifierError(f"{label} must be an origin-only HTTPS URL")
    if not host or any(character.isspace() for character in host):
        raise VerifierError(f"{label} must be an origin-only HTTPS URL")
    try:
        ipaddress.ip_address(host)
    except ValueError:
        labels = host.split(".")
        if any(not HOST_LABEL.fullmatch(part) for part in labels):
            raise VerifierError(f"{label} must be an origin-only HTTPS URL")
    if port is not None and not 1 <= port <= 65535:
        raise VerifierError(f"{label} must be an origin-only HTTPS URL")
    return value


def require_rp_id_for_origin(rp_id: str, canonical_origin: str) -> str:
    require_https_origin(canonical_origin, "canonical origin")
    try:
        origin_host = urlsplit(canonical_origin).hostname
    except ValueError:
        raise VerifierError("canonical origin must be an origin-only HTTPS URL") from None
    if not isinstance(rp_id, str) or not rp_id or rp_id.endswith("."):
        raise VerifierError("expected RP ID is malformed")
    try:
        ipaddress.ip_address(rp_id)
    except ValueError:
        if any(not HOST_LABEL.fullmatch(label) for label in rp_id.split(".")):
            raise VerifierError("expected RP ID is malformed")
    if origin_host != rp_id and not origin_host.endswith(f".{rp_id}"):
        raise VerifierError("expected RP ID is not registrable for the canonical origin")
    return rp_id


def atomic_write_private_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp_path = path.with_name(f".{path.name}.{secrets.token_hex(8)}.tmp")
    fd = os.open(tmp_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, PRIVATE_FILE_MODE)
    try:
        with os.fdopen(fd, "w") as handle:
            json.dump(payload, handle, indent=2, sort_keys=True)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_path, path)
        directory_fd = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
    finally:
        if tmp_path.exists():
            tmp_path.unlink()


def request_json(
    session: requests.Session,
    method: str,
    url: str,
    *,
    payload: dict | None = None,
    timeout: int = 30,
) -> requests.Response:
    try:
        return session.request(
            method,
            url,
            json=payload,
            timeout=timeout,
            allow_redirects=False,
        )
    except requests.RequestException:
        raise VerifierError(f"{method} request failed for {url}") from None


def response_json(response: requests.Response, label: str) -> dict:
    try:
        payload = response.json()
    except ValueError:
        raise VerifierError(f"{label} returned malformed JSON") from None
    if not isinstance(payload, dict):
        raise VerifierError(f"{label} returned a non-object response")
    return payload


def health(session: requests.Session, base_url: str) -> None:
    r = request_json(session, "GET", f"{base_url}/health", timeout=15)
    print(f"GET {base_url}/health -> {r.status_code}")
    if r.status_code != 200:
        raise VerifierError("health check failed; aborting passkey verification")


def default_credential_path(base_url: str, username: str) -> Path:
    host = urlparse(base_url).netloc or base_url.replace("://", "_").replace("/", "_")
    safe_host = "".join(c if c.isalnum() or c in "._-" else "_" for c in host)
    safe_username = "".join(c if c.isalnum() or c in "._-" else "_" for c in username)
    return Path(".logdate") / "passkey-verify" / safe_host / f"{safe_username}.json"


def save_credential(
    path: Path,
    base_url: str,
    origin: str,
    expected_rp_id: str,
    state: dict,
) -> None:
    payload = {
        "format": "logdate-passkey-verify-v2",
        "baseUrl": base_url,
        "origin": origin,
        "expectedRpId": expected_rp_id,
        "username": state["username"],
        "accountId": state.get("account_id") or account_id_from_user_handle(state["user_handle"]),
        "userHandle": b64u(state["user_handle"]),
        "credentialId": b64u(state["credential_id"]),
        "signCount": state.get("sign_count", 0),
        "privateKeyPem": private_key_to_pem(state["private_key"]),
        "updatedAt": datetime.now(timezone.utc).isoformat(),
    }
    atomic_write_private_json(path, payload)
    print(f"  saved passkey -> {path}")


def load_credential(
    path: Path,
    *,
    expected_origin: str | None = None,
    expected_username: str | None = None,
    expected_account_id: str | None = None,
    expected_base_url: str | None = None,
    allowed_previous_base_url: str | None = None,
    expected_credential_id: str | None = None,
    expected_rp_id: str | None = None,
) -> dict:
    require_private_file(path, "credential file")
    payload = json.loads(path.read_text())
    if payload.get("format") != "logdate-passkey-verify-v2":
        raise VerifierError(f"unsupported credential file format: {path}")
    if expected_origin is not None and payload.get("origin") != expected_origin:
        raise VerifierError("credential file origin does not match the requested canonical origin")
    if expected_rp_id is not None and payload.get("expectedRpId") != expected_rp_id:
        raise VerifierError("credential file RP ID does not match the deployment contract")
    if expected_username is not None and payload.get("username") != expected_username:
        raise VerifierError("credential file username does not match the requested username")
    if expected_account_id is not None and payload.get("accountId") != expected_account_id:
        raise VerifierError("credential file account ID does not match prepared state")
    credential_id = payload.get("credentialId")
    if expected_credential_id is not None and credential_id != expected_credential_id:
        raise VerifierError("credential file identity does not match prepared state")
    saved_base_url = payload.get("baseUrl")
    if expected_base_url is not None and saved_base_url != expected_base_url:
        if allowed_previous_base_url is None or saved_base_url != allowed_previous_base_url:
            raise VerifierError("credential file base URL does not match the allowed migration")
    user_handle = decode_registration_user_handle(payload.get("userHandle", ""))
    if expected_account_id is not None and account_id_from_user_handle(user_handle) != expected_account_id:
        raise VerifierError("credential user handle does not match prepared account")
    return {
        "credential_id": b64u_decode(credential_id),
        "private_key": private_key_from_pem(payload["privateKeyPem"]),
        "user_handle": user_handle,
        "account_id": payload.get("accountId"),
        "username": payload["username"],
        "expected_rp_id": payload.get("expectedRpId"),
        "sign_count": int(payload.get("signCount", 0)),
    }


def signup(
    session: requests.Session,
    base_url: str,
    origin: str,
    expected_rp_id: str,
    username: str,
    display_name: str,
) -> dict:
    print(f"\n=== SIGNUP {username} ===")
    requested_owner_id = str(uuid.uuid4())
    r = request_json(
        session,
        "POST",
        f"{base_url}/api/v1/auth/signup/passkey/begin",
        payload={
            "username": username,
            "displayName": display_name,
            "requestedOwnerId": requested_owner_id,
        },
    )
    print(f"begin -> {r.status_code}")
    if r.status_code != 200:
        raise VerifierError(f"signup/begin failed with HTTP {r.status_code}")
    try:
        body = response_json(r, "signup/begin")
        data = body["data"]
        session_token = data["sessionToken"]
        opts = data["registrationOptions"]
        challenge, rp_id = opts["challenge"], opts["rpId"]
        user_handle = decode_registration_user_handle(opts["user"]["id"])
        parameters = opts["pubKeyCredParams"]
        if not isinstance(parameters, list) or not any(
            isinstance(parameter, dict)
            and parameter.get("type") == "public-key"
            and parameter.get("alg") == -7
            for parameter in parameters
        ):
            raise VerifierError("signup options did not offer ES256")
        if rp_id != expected_rp_id:
            raise VerifierError("signup options RP ID does not match the deployment contract")
    except (KeyError, TypeError, AttributeError):
        raise VerifierError("signup/begin returned malformed registration options") from None
    print(f"  rpId={rp_id}  challenge.len={len(challenge)}")

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
    partial_state = {
        "credential_id": credential_id,
        "private_key": private_key,
        "user_handle": user_handle,
        "username": username,
        "expected_rp_id": expected_rp_id,
        "sign_count": 0,
    }
    r = request_json(
        session,
        "POST",
        f"{base_url}/api/v1/auth/signup/passkey/complete",
        payload={
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
    if 200 <= r.status_code < 300 and r.status_code != 201:
        raise SignupCompletedError(
            f"signup/complete returned unexpected HTTP {r.status_code}",
            partial_state,
        )
    if r.status_code != 201:
        raise VerifierError(f"signup/complete failed with HTTP {r.status_code}")
    try:
        auth = response_json(r, "signup/complete")
        bound_auth_values(auth, partial_state)
    except (KeyError, TypeError, AttributeError, VerifierError) as error:
        raise SignupCompletedError(str(error), partial_state) from error
    print(f"  body keys: {list(auth['data'].keys())}")
    partial_state["auth"] = auth
    return partial_state


def signin(
    session: requests.Session,
    base_url: str,
    origin: str,
    expected_rp_id: str,
    state: dict,
) -> dict:
    print(f"\n=== SIGNIN {state['username']} ===")
    r = request_json(
        session,
        "POST",
        f"{base_url}/api/v1/auth/signin/passkey/begin",
        payload={"username": state["username"]},
    )
    print(f"begin -> {r.status_code}")
    if r.status_code != 200:
        raise VerifierError(f"signin/begin failed with HTTP {r.status_code}")
    try:
        body = response_json(r, "signin/begin")
        data = body["data"]
        challenge = data["challenge"]
        rp_id = data["rpId"]
        allow = data["allowCredentials"]
        credential_id = b64u(state["credential_id"])
        print(f"  expected credential={credential_id}", file=sys.stderr)
        if rp_id != expected_rp_id:
            raise VerifierError("signin options RP ID does not match the deployment contract")
        if not isinstance(allow, list) or not any(
            isinstance(descriptor, dict)
            and descriptor.get("type") == "public-key"
            and descriptor.get("id") == credential_id
            for descriptor in allow
        ):
            print(f"  signin options allowCredentials={allow!r}", file=sys.stderr)
            print(f"  signin begin response={body!r}", file=sys.stderr)
            raise VerifierError("signin options did not allow the generated credential")
    except (KeyError, TypeError, AttributeError):
        raise VerifierError("signin/begin returned malformed authentication options") from None
    print(f"  rpId={rp_id}  allowCreds={len(allow)}  challenge.len={len(challenge)}")

    client_data = build_client_data("webauthn.get", challenge, origin)
    sign_count = state.get("sign_count", 0) + 1
    auth_data = build_auth_data(rp_id, sign_count=sign_count, include_attested=False)
    signature = state["private_key"].sign(
        auth_data + sha256(client_data),
        ec.ECDSA(hashes.SHA256()),
    )

    cred_id_b64 = credential_id
    r = request_json(
        session,
        "POST",
        f"{base_url}/api/v1/auth/signin/passkey/complete",
        payload={
            "challenge": challenge,
            "credential": {
                "id": cred_id_b64,
                "rawId": cred_id_b64,
                "response": {
                    "clientDataJSON": b64u(client_data),
                    "authenticatorData": b64u(auth_data),
                    "signature": b64u(signature),
                    "userHandle": b64u(state["user_handle"]),
                },
                "type": "public-key",
            },
        },
    )
    print(f"complete -> {r.status_code}")
    if r.status_code != 200:
        raise VerifierError(f"signin/complete failed with HTTP {r.status_code}")
    auth = response_json(r, "signin/complete")
    print(f"  body data keys: {list(auth.get('data', {}).keys())}")
    state["sign_count"] = sign_count
    return auth


def bound_auth_values(auth: dict, state: dict) -> tuple[str, str, str, str]:
    data = auth.get("data")
    if not isinstance(data, dict):
        raise VerifierError("auth response did not contain auth data")
    tokens = data.get("tokens") if isinstance(data.get("tokens"), dict) else data
    account = data.get("account") if isinstance(data.get("account"), dict) else None
    if not isinstance(tokens, dict) or not isinstance(account, dict):
        raise VerifierError("auth response omitted the bound account")
    access_token = tokens.get("accessToken")
    refresh_token = tokens.get("refreshToken")
    account_id = account.get("id")
    account_username = account.get("username")
    if not all(
        isinstance(value, str) and value
        for value in (access_token, refresh_token, account_id, account_username)
    ):
        raise VerifierError("auth response omitted account, username, or token state")
    try:
        canonical_account_id = str(uuid.UUID(account_id))
    except (ValueError, TypeError, AttributeError):
        raise VerifierError("auth response account ID is not a canonical UUID") from None
    if canonical_account_id != account_id:
        raise VerifierError("auth response account ID is not canonical")
    if account_username != state["username"]:
        raise VerifierError("auth response username does not match requested identity")
    if account_id_from_user_handle(state["user_handle"]) != account_id:
        raise VerifierError("registration user handle does not match auth account ID")
    return access_token, refresh_token, account_id, account_username


def deploy_state_from_auth(
    auth: dict,
    state: dict,
    credential_path: Path,
    existing: dict,
    base_url: str,
    origin: str,
) -> dict:
    access_token, refresh_token, account_id, _account_username = bound_auth_values(auth, state)

    credential_id = b64u(state["credential_id"])
    expected_account_id = existing.get("accountId")
    if expected_account_id is not None and account_id != expected_account_id:
        raise VerifierError("signin account does not match prepared state")
    expected_credential_id = existing.get("credentialId")
    if expected_credential_id is not None and credential_id != expected_credential_id:
        raise VerifierError("signin credential does not match prepared state")

    state["account_id"] = account_id

    existing_cleanup = existing.get("cleanup")
    if not isinstance(existing_cleanup, dict):
        existing_cleanup = {}
    cleanup = {
        "identityVerified": False,
        "mediaDeleted": existing_cleanup.get("mediaDeleted") is True,
        "refreshRevocationStarted": False,
        "refreshRevoked": False,
        "accountDeletionStarted": False,
        "accountDeleted": existing_cleanup.get("accountDeleted") is True,
    }

    payload = dict(existing)
    payload.update(
        {
            "format": DEPLOY_STATE_FORMAT,
            "username": state["username"],
            "accountId": account_id,
            "accessToken": access_token,
            "refreshToken": refresh_token,
            "credentialFile": str(credential_path.resolve()),
            "credentialId": credential_id,
            "userHandle": b64u(state["user_handle"]),
            "preparedBaseUrl": existing.get("preparedBaseUrl", base_url),
            "canonicalOrigin": origin,
            "expectedRpId": state["expected_rp_id"],
            "lastAuthenticatedBaseUrl": base_url,
            "recoveryMode": False,
            "cleanup": cleanup,
            "updatedAt": datetime.now(timezone.utc).isoformat(),
        }
    )
    return payload


def retain_recovery_state(
    state_path: Path | None,
    credential_path: Path,
    state: dict,
    base_url: str,
    origin: str,
    expected_rp_id: str,
) -> None:
    if state_path is None:
        return
    recovery = {
        "format": DEPLOY_STATE_FORMAT,
        "cleanupPending": True,
        "recoveryMode": True,
        "username": state["username"],
        "accountId": account_id_from_user_handle(state["user_handle"]),
        "userHandle": b64u(state["user_handle"]),
        "credentialFile": str(credential_path.resolve()),
        "credentialId": b64u(state["credential_id"]),
        "preparedBaseUrl": base_url,
        "canonicalOrigin": origin,
        "expectedRpId": expected_rp_id,
        "cleanup": {
            "identityVerified": False,
            "mediaDeleted": False,
            "refreshRevocationStarted": False,
            "refreshRevoked": False,
            "accountDeletionStarted": False,
            "accountDeleted": False,
        },
        "updatedAt": datetime.now(timezone.utc).isoformat(),
    }
    try:
        atomic_write_private_json(state_path, recovery)
    except OSError:
        pass


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--base", default="https://cloud.logdate.app")
    p.add_argument("--origin", default=None,
                   help="Origin in clientDataJSON; defaults to --base")
    p.add_argument("--expected-rp-id", default=None,
                   help="Immutable RP ID from the deployment contract")
    p.add_argument("--username", default=f"verify_{int(time.time())}")
    p.add_argument("--display-name", default="Deploy Verifier")
    p.add_argument("--credential-file", default=None,
                   help="Where to save/load the generated verifier passkey")
    p.add_argument("--signin-only", action="store_true",
                   help="Load --credential-file and only verify sign-in")
    p.add_argument("--state-file", default=None,
                   help="Permission-0600 deploy state file for account and token handoff")
    p.add_argument("--private-service-token-file", default=None,
                   help="Permission-0600 Cloud Run ID-token file for private candidate requests")
    p.add_argument("--credential-previous-base", default=None,
                   help="Expected candidate base URL when migrating a credential to canonical")
    p.add_argument("--atomic-replace-json", action="store_true",
                   help="Replace an existing private JSON file using the verifier's durable writer")
    p.add_argument("--input-json-file", default=None)
    p.add_argument("--output-json-file", default=None)
    p.add_argument("--validate-origin", default=None)
    p.add_argument("--certificate-fingerprint", default=None)
    p.add_argument("--apk-key-hash-origin", default=None)
    args = p.parse_args()
    if args.validate_origin is not None:
        require_https_origin(args.validate_origin, "origin")
        return
    if args.certificate_fingerprint is not None or args.apk_key_hash_origin is not None:
        if not args.certificate_fingerprint or not args.apk_key_hash_origin:
            raise VerifierError("certificate fingerprint and apk-key-hash origin are both required")
        if not apk_key_hash_matches_fingerprint(
            args.certificate_fingerprint, args.apk_key_hash_origin
        ):
            raise VerifierError("apk-key-hash origin does not match certificate fingerprint")
        return
    if args.atomic_replace_json:
        if not args.input_json_file or not args.output_json_file:
            raise VerifierError("--atomic-replace-json requires --input-json-file and --output-json-file")
        input_path = Path(args.input_json_file)
        output_path = Path(args.output_json_file)
        require_private_file(input_path, "input JSON file")
        require_private_file(output_path, "output JSON file", allow_empty=True)
        try:
            payload = json.loads(input_path.read_text())
        except (json.JSONDecodeError, OSError):
            raise VerifierError("input JSON file is not valid JSON")
        if not isinstance(payload, dict):
            raise VerifierError("input JSON file must contain an object")
        atomic_write_private_json(output_path, payload)
        return
    origin = args.origin or args.base
    require_https_origin(args.base, "--base")
    require_https_origin(origin, "--origin")
    if not args.expected_rp_id:
        raise VerifierError("--expected-rp-id is required")
    expected_rp_id = require_rp_id_for_origin(args.expected_rp_id, origin)
    credential_path = (
        Path(args.credential_file)
        if args.credential_file
        else default_credential_path(args.base, args.username)
    )

    session = requests.Session()
    if args.private_service_token_file:
        private_token_path = Path(args.private_service_token_file)
        require_private_file(private_token_path, "private service token file")
        token = private_token_path.read_text().strip()
        if not token or any(character.isspace() for character in token):
            raise VerifierError("private service token file is empty or malformed")
        session.headers["X-Serverless-Authorization"] = f"Bearer {token}"

    state_path = Path(args.state_file) if args.state_file else None
    existing_deploy_state: dict = {}
    if state_path is not None:
        require_private_file(state_path, "state file", allow_empty=True)
        if state_path.stat().st_size > 0:
            try:
                existing_deploy_state = json.loads(state_path.read_text())
            except (json.JSONDecodeError, OSError):
                raise VerifierError("state file is not valid JSON")

    print(f"base={args.base}  origin={origin}  username={args.username}")
    health(session, args.base)
    if args.signin_only:
        if not credential_path.exists():
            raise VerifierError(f"credential file not found: {credential_path}")
        state = load_credential(
            credential_path,
            expected_origin=origin,
            expected_username=args.username,
            expected_account_id=existing_deploy_state.get("accountId"),
            expected_base_url=args.base,
            allowed_previous_base_url=args.credential_previous_base,
            expected_credential_id=existing_deploy_state.get("credentialId"),
            expected_rp_id=expected_rp_id,
        )
        state["expected_rp_id"] = expected_rp_id
        print(f"loaded passkey <- {credential_path}")
    else:
        if credential_path.exists():
            raise VerifierError(
                f"credential file already exists: {credential_path}\n"
                "Use --signin-only to reuse it, or pass --credential-file to write elsewhere."
            )
        try:
            state = signup(
                session,
                args.base,
                origin,
                expected_rp_id,
                args.username,
                args.display_name,
            )
            state["expected_rp_id"] = expected_rp_id
        except SignupCompletedError as error:
            state = error.state
            try:
                save_credential(credential_path, args.base, origin, expected_rp_id, state)
            except OSError:
                pass
            retain_recovery_state(
                state_path,
                credential_path,
                state,
                args.base,
                origin,
                expected_rp_id,
            )
            raise VerifierError(
                "signup completed but returned unusable auth; recovery material retained"
            ) from error
        try:
            save_credential(credential_path, args.base, origin, expected_rp_id, state)
            provisional_state = deploy_state_from_auth(
                state["auth"], state, credential_path, existing_deploy_state, args.base, origin
            )
            if state_path is not None:
                atomic_write_private_json(state_path, provisional_state)
                existing_deploy_state = provisional_state
            save_credential(credential_path, args.base, origin, expected_rp_id, state)
        except (VerifierError, OSError, KeyError, TypeError) as error:
            retain_recovery_state(
                state_path,
                credential_path,
                state,
                args.base,
                origin,
                expected_rp_id,
            )
            raise VerifierError("failed to persist disposable-account cleanup state") from error

    auth = signin(session, args.base, origin, expected_rp_id, state)
    try:
        save_credential(credential_path, args.base, origin, expected_rp_id, state)
        deploy_state = deploy_state_from_auth(
            auth, state, credential_path, existing_deploy_state, args.base, origin
        )
        if state_path is not None:
            atomic_write_private_json(state_path, deploy_state)
    except (VerifierError, OSError, KeyError, TypeError) as error:
        retain_recovery_state(
            state_path,
            credential_path,
            state,
            args.base,
            origin,
            expected_rp_id,
        )
        raise VerifierError("failed to persist refreshed disposable-account cleanup state") from error
    print("\n=== END-TO-END PASSKEY VERIFICATION SUCCEEDED ===")


if __name__ == "__main__":
    try:
        main()
    except VerifierError as error:
        sys.exit(str(error))
