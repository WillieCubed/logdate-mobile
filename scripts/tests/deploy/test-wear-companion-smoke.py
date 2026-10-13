#!/usr/bin/env python3
"""Contract checks for the hosted watch-to-phone deployment journey."""
import importlib.util
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("wear_smoke", ROOT / "scripts/wear-companion-smoke.py")
smoke = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(smoke)


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


if __name__ == "__main__":
    unittest.main()
