#!/usr/bin/env python3
"""Contract checks for the hosted watch-to-phone deployment journey."""
import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("wear_smoke", ROOT / "scripts/wear-companion-smoke.py")
smoke = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(smoke)
FAKE_SPEC = importlib.util.spec_from_file_location("personal_space_fake", ROOT / "scripts/tests/lib/personal_space_fake.py")
space_fake = importlib.util.module_from_spec(FAKE_SPEC)
FAKE_SPEC.loader.exec_module(space_fake)
ORIGIN = "https://cloud.example.test"
ACCOUNT = "11111111-1111-4111-8111-111111111111"


class WearSmokeTest(unittest.TestCase):
    def setUp(self):
        self.fixture = smoke.fixture()
        self.fixture.update(sessionId="watch-session", watchToken="watch-token", watchRefreshToken="watch-refresh")
        self.calls = []
        self.deleted = False
        self.patched = False
        self.revoked = False
        self.corrupt = None
        self.probe = smoke.WearProbe("https://cloud.example.test", {"accessToken": "phone-token"}, self.request, None)

    def request(self, method, url, headers, body):
        self.calls.append((method, url, dict(headers), body))
        path = url.split("/api/v1")[-1]
        if path == "/auth/devices":
            return 200, {"devices": [] if self.revoked and self.corrupt != "device-list" else [{"id": "watch-session"}]}
        if path == "/auth/devices/watch-session":
            self.revoked = True
            return 204, {}
        if path == "/auth/token/refresh":
            return (200 if self.corrupt == "refresh" else 401), {}
        if path.startswith("/contents/"):
            if self.revoked and headers["Authorization"] == "Bearer watch-token":
                return (200 if self.corrupt == "revocation" else 401), {}
            return 200, self.fixture["capture"]
        if path.startswith("/contents?"):
            return 200, {"changes": [self.fixture["capture"]]}
        if path.startswith("/moments?"):
            return 200, {"changes": [self.fixture["moment"]],
                         "deletions": [] if self.corrupt == "tombstone" else [{"id": self.fixture["moment"]["id"]}]}
        if path.startswith("/moments/"):
            if method == "GET":
                if self.deleted:
                    return (200 if self.corrupt == "deleted-body" else 404), {}
                value = dict(self.fixture["moment"], serverVersion=10)
                if self.patched and self.corrupt != "caption":
                    value["payload"] = "LDSE2:deployment-caption"
                if self.corrupt == "payload":
                    value["payload"] = "changed"
                return 200, value
            if method == "PATCH":
                result = 409 if self.patched else 200
                self.patched = True
                return result, {}
            if method == "DELETE":
                self.deleted = True
                return 204, {}
            if method == "PUT":
                if "?ifAbsent=true" not in path:
                    raise AssertionError("Watch retry must use strict tombstone-aware create")
                return 410, {}
        raise AssertionError("Unexpected request")

    def test_caption_conflict_tombstone_and_revocation_are_verified(self):
        self.probe.finish(self.fixture)
        self.assertTrue(self.revoked)
        watch_calls = [call for call in self.calls if call[2]["Authorization"] == "Bearer watch-token"]
        self.assertTrue(watch_calls)
        self.assertTrue(any(call[0] == "PUT" and "ifAbsent=true" in call[1] for call in watch_calls))

    def test_revoked_access_refresh_tombstones_and_payload_fail_closed(self):
        for corruption in ("refresh", "revocation", "tombstone", "payload", "caption", "deleted-body", "device-list"):
            with self.subTest(corruption=corruption):
                self.setUp()
                self.corrupt = corruption
                with self.assertRaises(smoke.common.SmokeError):
                    self.probe.finish(self.fixture)

    def test_private_candidate_invoker_is_retained_on_watch_requests(self):
        self.probe.headers["X-Serverless-Authorization"] = "Bearer private-candidate"
        self.probe.finish(self.fixture)
        self.assertTrue(all(call[2]["X-Serverless-Authorization"] == "Bearer private-candidate" for call in self.calls))

    def test_failures_name_the_wear_journey_and_server_request(self):
        self.probe.transport = lambda *_: (404, {}, "wear-request")
        with self.assertRaisesRegex(smoke.common.SmokeError, r"^Wear companion smoke GET /contents returned HTTP 404 \(request wear-request\)$"):
            self.probe.request("GET", "/contents?since=0&limit=100")


class WearSpaceSmokeTest(unittest.TestCase):
    def setUp(self):
        self.server = space_fake.PersonalSpaceServer(ACCOUNT, "smoketest_fixture", ORIGIN)
        self.state = {"accessToken": "phone-token", "canonicalOrigin": ORIGIN, "accountId": ACCOUNT,
                      "username": "smoketest_fixture"}
        self.probe = smoke.WearProbe(ORIGIN, self.state, self.server.request, None)

    def prepared(self):
        self.probe.verify_identity()
        fixture = smoke.fixture(self.probe.storage_mode)
        self.probe.prepare(fixture)
        return fixture

    def test_space_account_runs_the_watch_journey_without_calling_a_legacy_route(self):
        fixture = self.prepared()
        self.assertEqual("personalSpaceV1", fixture["storageMode"])
        self.probe.finish(fixture)
        self.assertEqual([], self.server.legacy_calls)
        allowed = tuple("/api/v1" + path for path in space_fake.ACCOUNT_ROUTES + ("/auth/devices/link",))
        for _method, url, _headers, _body in self.server.calls:
            path = url.removeprefix(ORIGIN)
            self.assertTrue(path.startswith("/xrpc/app.logdate.space.") or path.startswith(allowed), path)
        watch_calls = [call for call in self.server.calls if call[2]["Authorization"] == "Bearer " + space_fake.WATCH_TOKEN]
        self.assertTrue(any(call[1].endswith("putRecord") for call in watch_calls))
        self.assertTrue(self.server.records[fixture["moment"]["rkey"]]["deleted"])
        self.assertFalse(self.server.records[fixture["capture"]["rkey"]]["deleted"])
        self.assertTrue(self.server.revoked)

    def test_space_journey_fails_closed_on_each_broken_guarantee(self):
        messages = {
            "swap-ignored": r"putRecord returned HTTP 200",
            "value": "Space record does not match what was written",
            "tombstone": "tombstone",
            "deleted-readable": r"getRecord returned HTTP 200",
            "revocation-ignored": r"getRecord returned HTTP 200",
            "refresh-ignored": r"token/refresh returned HTTP 200",
        }
        for corruption, message in messages.items():
            with self.subTest(corruption=corruption):
                self.setUp()
                self.server.corruption = corruption
                with self.assertRaisesRegex(smoke.common.SmokeError, message):
                    self.probe.finish(self.prepared())

    def test_unknown_mode_and_missing_protocol_fail_before_any_write(self):
        cases = (({"mode": "personalSpaceV2"}, "not covered by this smoke test"),
                 ({"mode": None}, "did not report a storageMode"),
                 ({"features": ["personalSpaceV1"]}, "missing a Wear companion protocol"))
        for overrides, message in cases:
            with self.subTest(overrides=overrides):
                self.setUp()
                server = space_fake.PersonalSpaceServer(ACCOUNT, "smoketest_fixture", ORIGIN, **overrides)
                probe = smoke.WearProbe(ORIGIN, self.state, server.request, None)
                with self.assertRaisesRegex(smoke.common.SmokeError, message):
                    probe.verify_identity()
                self.assertFalse(any(call[0] != "GET" for call in server.calls))


if __name__ == "__main__":
    unittest.main()
