#!/usr/bin/env python3
"""Focused regression tests for the deployment passkey verifier."""

import importlib.util
import json
import os
import stat
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


REPO_ROOT = Path(__file__).resolve().parents[3]
SIM_PATH = REPO_ROOT / "scripts/passkey-verify/sim.py"
SPEC = importlib.util.spec_from_file_location("logdate_passkey_sim", SIM_PATH)
assert SPEC and SPEC.loader
sim = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = sim
SPEC.loader.exec_module(sim)


ACCOUNT_ID = "11111111-1111-4111-8111-111111111111"
USERNAME = "smoke-user"


class FakeResponse:
    def __init__(self, status_code: int = 204, payload: object | None = None) -> None:
        self.status_code = status_code
        self.ok = 200 <= status_code < 400
        self._payload = payload

    def json(self) -> object:
        if isinstance(self._payload, Exception):
            raise self._payload
        return self._payload


class FakeSession:
    def __init__(self, statuses: list[int] | None = None) -> None:
        self.headers: dict[str, str] = {}
        self.calls: list[tuple[str, str, dict]] = []
        self.statuses = list(statuses or [])

    def request(self, method: str, url: str, **kwargs: object) -> FakeResponse:
        self.calls.append((method, url, kwargs))
        return FakeResponse(self.statuses.pop(0) if self.statuses else 204)


class PasskeyVerifierRegressionTests(unittest.TestCase):
    def credential_payload(self, **overrides: object) -> dict:
        payload = {
            "format": "logdate-passkey-verify-v2",
            "baseUrl": "https://candidate.example.test",
            "origin": "https://cloud.example.test",
            "expectedRpId": "example.test",
            "username": USERNAME,
            "accountId": ACCOUNT_ID,
            "userHandle": sim.b64u(ACCOUNT_ID.encode()),
            "credentialId": "Y3JlZGVudGlhbA",
            "signCount": 0,
            "privateKeyPem": "not-used-before-binding-check",
        }
        payload.update(overrides)
        return payload

    def write_credential(self, directory: str, payload: dict) -> Path:
        credential = Path(directory) / "credential.json"
        credential.write_text(json.dumps(payload))
        credential.chmod(0o600)
        return credential

    def test_atomic_write_fsyncs_file_and_destination_directory(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "state.json"
            real_fsync = os.fsync
            fsynced_modes: list[int] = []

            def recording_fsync(fd: int) -> None:
                fsynced_modes.append(stat.S_IFMT(os.fstat(fd).st_mode))
                real_fsync(fd)

            with mock.patch.object(sim.os, "fsync", side_effect=recording_fsync):
                sim.atomic_write_private_json(destination, {"safe": True})

            self.assertIn(stat.S_IFREG, fsynced_modes)
            self.assertIn(stat.S_IFDIR, fsynced_modes)
            self.assertEqual(0o600, stat.S_IMODE(destination.stat().st_mode))

    def test_https_origin_parser_accepts_ipv6_and_rejects_non_origins(self) -> None:
        for origin in (
            "https://example.test",
            "https://example.test:8443",
            "https://[2001:db8::1]",
            "https://[2001:db8::1]:8443",
        ):
            with self.subTest(valid=origin):
                self.assertEqual(origin, sim.require_https_origin(origin, "origin"))
        for origin in (
            "https://",
            "https://example.test.",
            "https://user@example.test",
            "https://example.test/path",
            "https://example.test?query",
            "https://example.test#fragment",
            "https://example.test:0",
            "https://example.test:65536",
            "https://example.test:",
            "https://[2001:db8::1",
            "\thttps://example.test",
            "https://exam\nple.test",
            "https://example.test\r",
            "http://example.test",
        ):
            with self.subTest(invalid=origin), self.assertRaises(sim.VerifierError):
                sim.require_https_origin(origin, "origin")

    def test_load_credential_checks_each_binding_independently(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            cases = {
                "origin": {"origin": "https://wrong.example.test"},
                "username": {"username": "wrong-user"},
                "account": {"accountId": "22222222-2222-4222-8222-222222222222"},
                "credential": {"credentialId": "d3Jvbmc"},
                "base": {"baseUrl": "https://replacement.example.test"},
                "rp": {"expectedRpId": "wrong.test"},
            }
            for label, overrides in cases.items():
                with self.subTest(label=label):
                    credential = self.write_credential(directory, self.credential_payload(**overrides))
                    with self.assertRaises(sim.VerifierError):
                        sim.load_credential(
                            credential,
                            expected_origin="https://cloud.example.test",
                            expected_username=USERNAME,
                            expected_account_id=ACCOUNT_ID,
                            expected_base_url="https://cloud.example.test",
                            allowed_previous_base_url="https://candidate.example.test",
                            expected_credential_id="Y3JlZGVudGlhbA",
                            expected_rp_id="example.test",
                        )

    def test_real_registration_handle_binds_to_plain_account_uuid(self) -> None:
        encoded_handle = sim.b64u(ACCOUNT_ID.encode())
        handle = sim.decode_registration_user_handle(encoded_handle)
        self.assertEqual(ACCOUNT_ID.encode(), handle)
        state = {
            "credential_id": b"credential",
            "user_handle": handle,
            "username": USERNAME,
            "expected_rp_id": "example.test",
        }
        auth = {
            "data": {
                "account": {"id": ACCOUNT_ID, "username": USERNAME},
                "tokens": {"accessToken": "access-token", "refreshToken": "refresh-token"},
            }
        }
        deploy = sim.deploy_state_from_auth(
            auth,
            state,
            Path("credential.json"),
            {},
            "https://candidate.example.test",
            "https://cloud.example.test",
        )
        self.assertEqual(ACCOUNT_ID, deploy["accountId"])
        self.assertEqual(encoded_handle, deploy["userHandle"])

    def test_auth_requires_bound_account_id_and_username(self) -> None:
        state = {
            "credential_id": b"credential",
            "user_handle": ACCOUNT_ID.encode(),
            "username": USERNAME,
        }
        for account in ({}, {"id": ACCOUNT_ID}, {"id": ACCOUNT_ID, "username": "swapped"}):
            with self.subTest(account=account), self.assertRaises(sim.VerifierError):
                sim.deploy_state_from_auth(
                    {"data": {"account": account, "tokens": {"accessToken": "a", "refreshToken": "r"}}},
                    state,
                    Path("credential.json"),
                    {},
                    "https://candidate.example.test",
                    "https://cloud.example.test",
                )

    def test_malformed_success_response_raises_catchable_error(self) -> None:
        with self.assertRaises(sim.VerifierError):
            sim.response_json(FakeResponse(200, ValueError("truncated")), "signup/complete")

    def test_requests_disable_redirects(self) -> None:
        session = FakeSession([302])
        session.headers["Authorization"] = "Bearer secret-that-must-not-redirect"
        response = sim.request_json(session, "GET", "https://candidate.example.test/health")
        self.assertEqual(302, response.status_code)
        self.assertFalse(session.calls[0][2]["allow_redirects"])
        self.assertEqual(1, len(session.calls))

    def test_health_rejects_redirect_without_forwarding_session_headers(self) -> None:
        session = FakeSession([302])
        session.headers["X-Serverless-Authorization"] = "Bearer private-secret"
        with self.assertRaises(sim.VerifierError):
            sim.health(session, "https://candidate.example.test")
        self.assertEqual(1, len(session.calls))
        self.assertFalse(session.calls[0][2]["allow_redirects"])

    def test_rp_relationship_rejects_unrelated_domain(self) -> None:
        with self.assertRaises(sim.VerifierError):
            sim.require_rp_id_for_origin("unrelated.test", "https://cloud.example.test")
        self.assertEqual(
            "example.test",
            sim.require_rp_id_for_origin("example.test", "https://cloud.example.test"),
        )

    def test_signup_rejects_wrong_rp_and_missing_es256(self) -> None:
        for label, rp_id, algorithms in (
            ("wrong-rp", "wrong.test", [-7]),
            ("missing-es256", "example.test", [-257]),
        ):
            begin = {
                "data": {
                    "sessionToken": "session",
                    "registrationOptions": {
                        "challenge": "challenge",
                        "rpId": rp_id,
                        "user": {"id": sim.b64u(ACCOUNT_ID.encode())},
                        "pubKeyCredParams": [
                            {"type": "public-key", "alg": algorithm}
                            for algorithm in algorithms
                        ],
                    },
                }
            }
            with self.subTest(label=label), mock.patch.object(
                sim, "request_json", return_value=FakeResponse(200, begin)
            ), self.assertRaises(sim.VerifierError):
                sim.signup(
                    FakeSession(), "https://candidate.example.test",
                    "https://cloud.example.test", "example.test", USERNAME, "Smoke",
                )

    def test_signin_rejects_allow_credentials_without_generated_credential(self) -> None:
        state = {
            "credential_id": b"credential",
            "private_key": sim.ec.generate_private_key(sim.ec.SECP256R1()),
            "user_handle": ACCOUNT_ID.encode(),
            "username": USERNAME,
            "sign_count": 0,
        }
        for label, rp_id, credential_id in (
            ("wrong-rp", "wrong.test", sim.b64u(b"credential")),
            ("wrong-credential", "example.test", sim.b64u(b"other")),
        ):
            begin = {
                "data": {
                    "challenge": "challenge",
                    "rpId": rp_id,
                    "allowCredentials": [{"type": "public-key", "id": credential_id}],
                }
            }
            with self.subTest(label=label), mock.patch.object(
                sim, "request_json", return_value=FakeResponse(200, begin)
            ), self.assertRaises(sim.VerifierError):
                sim.signin(
                    FakeSession(), "https://cloud.example.test",
                    "https://cloud.example.test", "example.test", state,
                )

    def test_successful_signup_with_non_object_data_becomes_recoverable_error(self) -> None:
        begin = {
            "data": {
                "sessionToken": "session",
                "registrationOptions": {
                    "challenge": "challenge",
                    "rpId": "example.test",
                    "user": {"id": sim.b64u(ACCOUNT_ID.encode())},
                    "pubKeyCredParams": [{"type": "public-key", "alg": -7}],
                },
            }
        }
        complete = {"data": []}
        with mock.patch.object(
            sim,
            "request_json",
            side_effect=[FakeResponse(200, begin), FakeResponse(201, complete)],
        ):
            with self.assertRaises(sim.SignupCompletedError):
                sim.signup(
                    FakeSession(), "https://candidate.example.test",
                    "https://cloud.example.test", "example.test", USERNAME, "Smoke",
                )

    def test_apk_key_hash_is_derived_from_exact_32_byte_fingerprint(self) -> None:
        fingerprint = ":".join(f"{value:02X}" for value in range(32))
        origin = sim.apk_key_hash_origin_from_fingerprint(fingerprint)
        self.assertTrue(origin.startswith("android:apk-key-hash:"))
        self.assertEqual(43, len(origin.removeprefix("android:apk-key-hash:")))
        self.assertTrue(sim.apk_key_hash_matches_fingerprint(fingerprint, origin))
        self.assertFalse(sim.apk_key_hash_matches_fingerprint(fingerprint, f"{origin[:-1]}A"))

    def test_unbound_signup_auth_is_retained_without_token_use(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            state_path = Path(directory) / "state.json"
            state_path.touch(mode=0o600)
            state_path.chmod(0o600)
            credential_path = Path(directory) / "credential.json"
            session = FakeSession()
            signup_state = {
                "credential_id": b"credential",
                "private_key": sim.ec.generate_private_key(sim.ec.SECP256R1()),
                "user_handle": ACCOUNT_ID.encode(),
                "username": USERNAME,
                "sign_count": 0,
                "auth": {
                    "data": {
                        "account": {"id": ACCOUNT_ID},
                        "tokens": {"accessToken": "unbound", "refreshToken": "unbound"},
                    }
                },
            }
            argv = [
                "sim.py", "--base", "https://candidate.example.test",
                "--origin", "https://cloud.example.test", "--username", USERNAME,
                "--expected-rp-id", "example.test",
                "--credential-file", str(credential_path), "--state-file", str(state_path),
            ]
            with (
                mock.patch.object(sim.requests, "Session", return_value=session),
                mock.patch.object(sim, "health"),
                mock.patch.object(sim, "signup", return_value=signup_state),
                mock.patch.object(sys, "argv", argv),
                self.assertRaises(sim.VerifierError),
            ):
                sim.main()
            self.assertEqual([], session.calls)
            self.assertTrue(credential_path.exists())
            recovery = json.loads(state_path.read_text())
            self.assertEqual("logdate-deploy-smoke-v2", recovery["format"])
            self.assertTrue(recovery["recoveryMode"])
            self.assertEqual(ACCOUNT_ID, json.loads(credential_path.read_text())["accountId"])

    def test_state_handoff_failure_does_not_delete_before_reauthentication(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            state_path = Path(directory) / "state.json"
            state_path.touch(mode=0o600)
            state_path.chmod(0o600)
            credential_path = Path(directory) / "credential.json"
            session = FakeSession([204, 200])
            signup_state = {
                "credential_id": b"credential",
                "private_key": object(),
                "user_handle": ACCOUNT_ID.encode(),
                "username": USERNAME,
                "sign_count": 0,
                "auth": {
                    "data": {
                        "account": {"id": ACCOUNT_ID, "username": USERNAME},
                        "tokens": {
                            "accessToken": "access-token",
                            "refreshToken": "refresh-token",
                        },
                    }
                },
            }
            argv = [
                "sim.py",
                "--base",
                "https://candidate.example.test",
                "--origin",
                "https://cloud.example.test",
                "--username",
                USERNAME,
                "--expected-rp-id",
                "example.test",
                "--credential-file",
                str(credential_path),
                "--state-file",
                str(state_path),
            ]

            with (
                mock.patch.object(sim.requests, "Session", return_value=session),
                mock.patch.object(sim, "health"),
                mock.patch.object(sim, "signup", return_value=signup_state),
                mock.patch.object(sim, "save_credential"),
                mock.patch.object(sim, "atomic_write_private_json", side_effect=OSError("disk full")),
                mock.patch.object(sys, "argv", argv),
                self.assertRaises(sim.VerifierError),
            ):
                sim.main()

            self.assertEqual([], session.calls)

    def test_real_shaped_prepare_then_canonical_verify_preserves_identity(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            state_path = Path(directory) / "state.json"
            state_path.touch(mode=0o600)
            state_path.chmod(0o600)
            credential_path = Path(directory) / "credential.json"
            auth = {
                "data": {
                    "account": {"id": ACCOUNT_ID, "username": USERNAME},
                    "tokens": {"accessToken": "access-token", "refreshToken": "refresh-token"},
                }
            }
            signup_state = {
                "credential_id": b"credential",
                "private_key": sim.ec.generate_private_key(sim.ec.SECP256R1()),
                "user_handle": ACCOUNT_ID.encode(),
                "username": USERNAME,
                "sign_count": 0,
                "auth": auth,
            }
            prepare_argv = [
                "sim.py", "--base", "https://candidate.example.test",
                "--origin", "https://cloud.example.test", "--username", USERNAME,
                "--expected-rp-id", "example.test",
                "--credential-file", str(credential_path), "--state-file", str(state_path),
            ]
            with (
                mock.patch.object(sim.requests, "Session", return_value=FakeSession()),
                mock.patch.object(sim, "health"),
                mock.patch.object(sim, "signup", return_value=signup_state),
                mock.patch.object(sim, "signin", return_value=auth),
                mock.patch.object(sys, "argv", prepare_argv),
            ):
                sim.main()

            prepared = json.loads(state_path.read_text())
            self.assertEqual(ACCOUNT_ID, prepared["accountId"])
            self.assertEqual(sim.b64u(ACCOUNT_ID.encode()), prepared["userHandle"])
            verify_argv = [
                "sim.py", "--base", "https://cloud.example.test",
                "--origin", "https://cloud.example.test", "--username", USERNAME,
                "--expected-rp-id", "example.test",
                "--credential-file", str(credential_path), "--state-file", str(state_path),
                "--credential-previous-base", "https://candidate.example.test", "--signin-only",
            ]
            with (
                mock.patch.object(sim.requests, "Session", return_value=FakeSession()),
                mock.patch.object(sim, "health"),
                mock.patch.object(sim, "signin", return_value=auth),
                mock.patch.object(sys, "argv", verify_argv),
            ):
                sim.main()

            verified = json.loads(state_path.read_text())
            self.assertEqual(ACCOUNT_ID, verified["accountId"])
            self.assertEqual("https://candidate.example.test", verified["preparedBaseUrl"])
            self.assertEqual("https://cloud.example.test", verified["lastAuthenticatedBaseUrl"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
