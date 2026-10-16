#!/usr/bin/env python3
"""Acceptance command failures must fail closed and clean up disposable accounts."""
import contextlib
import importlib.util
import io
import unittest
from pathlib import Path
from unittest.mock import patch

path = Path(__file__).resolve().parents[2] / "passkey-verify/restore.py"
spec = importlib.util.spec_from_file_location("restore_smoke", path)
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)
BASE = "https://server.example"
ORIGIN = "https://app.example"
RP = "example"
OWNER = "11111111-1111-4111-8111-111111111111"


class Response:
    def __init__(self, status, data=None):
        self.status_code = status
        self.data = data
    def json(self):
        return self.data


class Session:
    def __init__(self, rejects=True, cleanup=204, broken_registration=False, reusable=False, wrong_token_owner=False):
        self.rejects = rejects
        self.reusable = reusable
        self.wrong_token_owner = wrong_token_owner
        self.deleted_token = None
        self.cleanup = cleanup
        self.broken_registration = broken_registration
        self.deleted = False
        self.assertions = []
        self.get_calls = 0
    def request(self, method, url, **kwargs):
        if method == "DELETE":
            self.deleted = True
            self.deleted_token = kwargs["headers"].get("Authorization")
            return Response(self.cleanup)
        if method == "GET" and url.endswith("auth/me"):
            owner = OWNER
            if self.wrong_token_owner and kwargs["headers"].get("Authorization") == "Bearer restored-secret":
                owner = "22222222-2222-4222-8222-222222222222"
            return Response(200, {"data": {"account": {"id": owner}}})
        if url.endswith("register/begin"):
            if self.broken_registration:
                return Response(503)
            return Response(200, {"data": {"rpId": RP, "challenge": "Y2hhbGxlbmdl"}})
        if url.endswith("register/complete"):
            return Response(200, {"success": True})
        if url.endswith("restore/begin"):
            self.get_calls += 1
            return Response(200, {"data": {"rpId": RP, "challenge": str(self.get_calls), "userVerification": "discouraged"}})
        payload = kwargs["json"]
        self.assertions.append(payload)
        if len(self.assertions) == 3:
            return Response(200, {"data": {"account": {"id": OWNER}, "tokens": {"accessToken": "restored-secret"}}})
        if len(self.assertions) == 5 and self.reusable:
            raw = smoke.sim.b64u_decode(payload["credential"]["response"]["authenticatorData"])
            return Response(200 if int.from_bytes(raw[33:37], "big") > 1 else 401)
        return Response(401 if self.rejects else 200)


class RestoreSmokeTests(unittest.TestCase):
    def run_smoke(self, session):
        auth = {"auth": {"data": {"account": {"id": OWNER}, "tokens": {"accessToken": "fixture-secret"}}}}
        with patch.object(smoke.sim, "signup", return_value=auth):
            return smoke.run_acceptance(session, BASE, ORIGIN, RP)

    def test_signed_passive_flow_and_cleanup_do_not_print_tokens(self):
        session = Session()
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.run_smoke(session)
        self.assertTrue(session.deleted)
        self.assertEqual(5, len(session.assertions))
        data = smoke.sim.b64u_decode(session.assertions[2]["credential"]["response"]["authenticatorData"])
        self.assertEqual(0, data[32], "The success case must not assert UP or UV")
        self.assertNotIn("fixture-secret", output.getvalue())
        self.assertNotIn("restored-secret", output.getvalue())

    def test_a_server_accepting_invalid_signature_fails_acceptance_and_cleans_up(self):
        session = Session(rejects=False)
        with self.assertRaises(smoke.sim.VerifierError):
            self.run_smoke(session)
        self.assertTrue(session.deleted)

    def test_registration_failure_still_deletes_the_disposable_account(self):
        session = Session(broken_registration=True)
        with self.assertRaises(smoke.sim.VerifierError):
            self.run_smoke(session)
        self.assertTrue(session.deleted)

    def test_cleanup_failure_is_not_reported_as_acceptance(self):
        session = Session(cleanup=503)
        with self.assertRaises(smoke.sim.VerifierError):
            self.run_smoke(session)
        self.assertTrue(session.deleted)

    def test_fresh_counter_exposes_a_credential_that_was_not_consumed(self):
        session = Session(reusable=True)
        with self.assertRaises(smoke.sim.VerifierError):
            self.run_smoke(session)
        self.assertTrue(session.deleted)

    def test_wrong_restore_token_owner_never_controls_account_cleanup(self):
        session = Session(wrong_token_owner=True)
        with self.assertRaises(smoke.sim.VerifierError):
            self.run_smoke(session)
        self.assertTrue(session.deleted)
        self.assertEqual("Bearer fixture-secret", session.deleted_token)

    def test_completed_signup_failure_recovers_identity_for_cleanup(self):
        session = Session()
        state = {"user_handle": OWNER.encode()}
        failure = smoke.sim.SignupCompletedError("malformed completion", state)
        recovered = {"data": {"account": {"id": OWNER}, "tokens": {"accessToken": "cleanup-secret"}}}
        with patch.object(smoke.sim, "signup", side_effect=failure), patch.object(smoke.sim, "signin", return_value=recovered):
            with self.assertRaises(smoke.sim.VerifierError):
                smoke.run_acceptance(session, BASE, ORIGIN, RP)
        self.assertTrue(session.deleted)
        self.assertEqual("Bearer cleanup-secret", session.deleted_token)


if __name__ == "__main__":
    unittest.main()
