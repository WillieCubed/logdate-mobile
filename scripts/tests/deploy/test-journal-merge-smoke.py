#!/usr/bin/env python3
"""Behavioral checks for the deployment journal merge proof."""

import contextlib
import copy
import importlib.util
import io
import json
import tempfile
import unittest
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("journal_merge_smoke", ROOT / "scripts/journal-merge-smoke.py")
smoke = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(smoke)
FAKE_SPEC = importlib.util.spec_from_file_location("personal_space_fake", ROOT / "scripts/tests/lib/personal_space_fake.py")
space_fake = importlib.util.module_from_spec(FAKE_SPEC)
FAKE_SPEC.loader.exec_module(space_fake)
CANDIDATE = "https://candidate.example.test"
ORIGIN = "https://cloud.example.test"
WEBAUTHN_ORIGIN = "https://app.example.test"
ACCOUNT = str(uuid.uuid4())
ACCESS = "access-token-do-not-log"
PRIVATE = "private-token-do-not-log"


class JournalServer:
    def __init__(self):
        self.journals = {}
        self.links = set()
        self.deletions = []
        self.operations = {}
        self.calls = []
        self.features = ["journalMergeV1"]
        self.account = ACCOUNT
        self.corruption = None

    def request(self, method, url, headers, body):
        self.calls.append((method, url, dict(headers), copy.deepcopy(body)))
        path = url.split("/api/v1", 1)[-1]
        if path == "/server/info":
            return 200, {"success": True, "data": {"serverOrigin": ORIGIN, "protocolFeatures": self.features}}
        if path == "/auth/me":
            return 200, {"success": True, "data": {"account": {"id": self.account, "username": "smoketest_fixture", "storageMode": "legacy"}}}
        if path.startswith("/journals/") and path.endswith("/merge"):
            source = path.split("/")[2]
            destination = body["destinationId"]
            operation = body["operationId"]
            if operation not in self.operations:
                for journal, content in list(self.links):
                    if journal == source:
                        self.links.remove((journal, content))
                        self.links.add((destination, content))
                for content in body["contentIds"]:
                    self.links.add((destination, content))
                self.journals.pop(source, None)
                self.deletions.append({"id": source, "mergedIntoJournalId": destination})
                self.operations[operation] = {"operationId": operation, "sourceId": source, "destinationId": destination}
            response = dict(self.operations[operation])
            if self.corruption == "retry" and sum(1 for call in self.calls if call[1].endswith("/merge")) > 1:
                response["operationId"] = str(uuid.uuid4())
            return 200, response
        if path.startswith("/journals/"):
            journal = path.split("/")[2]
            if method == "PUT":
                self.journals[journal] = dict(body)
                return 201, {"id": journal}
            if journal not in self.journals:
                return 404, {}
            result = dict(self.journals[journal])
            if self.corruption == "metadata":
                result["description"] = "changed"
            return 200, result
        if path.startswith("/journals?"):
            deletions = [] if self.corruption == "tombstone" else self.deletions
            return 200, {"changes": list(self.journals.values()), "deletions": deletions, "hasMore": False}
        if path == "/associations" and method == "POST":
            self.links.update((item["journalId"], item["contentId"]) for item in body["associations"])
            return 200, {"uploadedCount": len(body["associations"])}
        if path.startswith("/associations?"):
            links = [] if self.corruption == "membership" else self.links
            return 200, {"changes": [{"journalId": j, "contentId": c} for j, c in links], "deletions": [], "hasMore": False}
        raise AssertionError(f"Unexpected request {method} {path}")


class Response:
    def __init__(self, status, body=b"{}", request_id=None):
        self.status = status
        self.body = body
        self.headers = {} if request_id is None else {"x-request-id": request_id}

    def getheader(self, name, default=None):
        return self.headers.get(name.lower(), default)

    def read(self, limit):
        return self.body[:limit]


class Connection:
    def __init__(self, outcome):
        self.outcome = outcome
        self.sent = []

    def request(self, method, target, body=None, headers=None):
        self.sent.append((method, target, body, headers))

    def getresponse(self):
        if isinstance(self.outcome, BaseException):
            raise self.outcome
        return self.outcome

    def close(self):
        pass


class Network:
    """Each outcome is a response, an exception raised after sending, or ("connect", exception)."""

    def __init__(self, *outcomes):
        self.outcomes = list(outcomes)
        self.connections = []
        self.delays = []
        self.log = io.StringIO()

    def connect(self, url):
        outcome = self.outcomes.pop(0)
        if isinstance(outcome, tuple):
            raise outcome[1]
        connection = Connection(outcome)
        self.connections.append(connection)
        return connection

    def request(self, method, url, body=None):
        with contextlib.redirect_stderr(self.log):
            return smoke.http_request(method, url, {}, body, connect=self.connect, sleep=self.delays.append)


@contextlib.contextmanager
def patch_transport(server, result):
    original = server.request
    server.request = lambda *_: result
    try:
        yield
    finally:
        server.request = original


class HttpTransportTest(unittest.TestCase):
    def test_unrouted_front_end_not_found_is_retried_until_the_server_answers(self):
        network = Network(Response(404, b"<html>Page not found</html>"), Response(200, b'{"changes": []}', "app"))
        self.assertEqual((200, {"changes": []}, "app"), network.request("GET", CANDIDATE + "/api/v1/contents?since=0"))
        self.assertEqual([1], network.delays)
        self.assertIn("GET /contents", network.log.getvalue())
        self.assertEqual("/api/v1/contents?since=0", network.connections[-1].sent[0][1])

    def test_persistent_front_end_not_found_never_satisfies_an_expected_not_found(self):
        network = Network(*[Response(404, b"<html>Page not found</html>")] * smoke.TRANSPORT_ATTEMPTS)
        probe = smoke.Probe(CANDIDATE, {"accessToken": ACCESS}, lambda *call: network.request(call[0], call[1], call[3]), None)
        with self.assertRaisesRegex(
            smoke.SmokeError,
            r"Journal merge smoke GET /journals/missing returned HTTP 404 from the Cloud Run front end, not the server \(after 6 attempts\)",
        ):
            probe.request("GET", "/journals/missing", statuses=(404,))
        self.assertEqual([1, 2, 4, 8, 8], network.delays)

    def test_front_end_timeout_is_reported_without_retrying(self):
        network = Network(Response(504, b"upstream request timeout"))
        with self.assertRaisesRegex(smoke.SmokeError, r"POST /journals/a/merge returned HTTP 504 from the Cloud Run front end, not the server$"):
            network.request("POST", CANDIDATE + "/api/v1/journals/a/merge", {"operationId": "op"})
        self.assertEqual([], network.delays)

    def test_connection_failures_before_sending_are_retried(self):
        network = Network(("connect", ConnectionRefusedError()), ("connect", TimeoutError()), Response(201, b"{}", "app"))
        self.assertEqual((201, {}, "app"), network.request("PUT", CANDIDATE + "/api/v1/journals/a", {"id": "a"}))
        self.assertEqual([1, 2], network.delays)
        self.assertEqual(1, len(network.connections))

    def test_persistent_connection_failure_names_the_request_and_error(self):
        network = Network(*[("connect", ConnectionRefusedError())] * smoke.TRANSPORT_ATTEMPTS)
        with self.assertRaisesRegex(smoke.SmokeError, r"GET /server/info could not connect \(ConnectionRefusedError\) \(after 6 attempts\)"):
            network.request("GET", CANDIDATE + "/api/v1/server/info")

    def test_unanswered_request_is_reported_without_retrying(self):
        network = Network(TimeoutError(), Response(200, b"{}", "app"))
        with self.assertRaisesRegex(smoke.SmokeError, r"POST /journals/a/merge did not respond within 30 seconds"):
            network.request("POST", CANDIDATE + "/api/v1/journals/a/merge", {"operationId": "op"})
        self.assertEqual(1, len(network.connections))

    def test_connection_lost_after_sending_is_reported_without_retrying(self):
        network = Network(ConnectionResetError(), Response(200, b"{}", "app"))
        with self.assertRaisesRegex(smoke.SmokeError, r"PUT /moments/m lost its connection before a response \(ConnectionResetError\)"):
            network.request("PUT", CANDIDATE + "/api/v1/moments/m", {"id": "m"})
        self.assertEqual(1, len(network.connections))

    def test_server_errors_are_returned_for_the_probe_to_report(self):
        network = Network(Response(500, b'{"error": "boom"}', "app-500"))
        self.assertEqual((500, {"error": "boom"}, "app-500"), network.request("GET", CANDIDATE + "/api/v1/auth/me"))
        self.assertEqual([], network.delays)

    def test_redirects_and_plain_http_are_rejected(self):
        with self.assertRaisesRegex(smoke.SmokeError, "GET /auth/me attempted a redirect"):
            Network(Response(301, b"", "app")).request("GET", CANDIDATE + "/api/v1/auth/me")
        with self.assertRaisesRegex(smoke.SmokeError, "HTTPS"):
            smoke.http_request("GET", "http://candidate.example.test/api/v1/auth/me", {}, None)


class JournalMergeSmokeTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.state_file = Path(self.directory.name) / "state.json"
        self.credential_file = Path(self.directory.name) / "passkey.json"
        self.private_file = Path(self.directory.name) / "invoker"
        self.private_file.write_text(PRIVATE)
        self.private_file.chmod(0o600)
        credential = {
            "accountId": ACCOUNT, "username": "smoketest_fixture", "origin": WEBAUTHN_ORIGIN,
            "expectedRpId": "example.test", "credentialId": "Y3JlZGVudGlhbA", "userHandle": "aGFuZGxl",
        }
        self.credential_file.write_text(json.dumps(credential))
        self.credential_file.chmod(0o600)
        self.state = {
            "format": "logdate-deploy-smoke-v2", "accountId": ACCOUNT, "username": "smoketest_fixture",
            "accessToken": ACCESS, "refreshToken": "refresh-do-not-log", "canonicalOrigin": ORIGIN, "webauthnOrigin": WEBAUTHN_ORIGIN,
            "preparedBaseUrl": CANDIDATE, "expectedRpId": "example.test",
            "credentialFile": str(self.credential_file), "credentialId": "Y3JlZGVudGlhbA", "userHandle": "aGFuZGxl",
            "cleanup": {"accountDeleted": False}, "mediaId": "existing-media",
        }
        self.write_state()
        self.server = JournalServer()

    def write_state(self):
        self.state_file.write_text(json.dumps(self.state))
        self.state_file.chmod(0o600)

    def probe(self, phase="prepare"):
        smoke.run_probe(
            CANDIDATE if phase == "prepare" else ORIGIN, self.state_file, phase,
            self.private_file if phase == "prepare" else None, self.server.request,
        )

    def test_candidate_merge_retry_and_promoted_durability_use_same_fixture(self):
        self.probe()
        prepared = json.loads(self.state_file.read_text())
        self.assertEqual("existing-media", prepared["mediaId"])
        self.assertEqual(0o600, self.state_file.stat().st_mode & 0o777)
        merges = [call for call in self.server.calls if call[1].endswith("/merge")]
        self.assertEqual(2, len(merges))
        self.assertEqual(merges[0][3], merges[1][3])
        self.assertTrue(all(call[2].get("X-Serverless-Authorization") == f"Bearer {PRIVATE}" for call in self.server.calls))
        candidate_count = len(self.server.calls)
        self.probe("verify")
        promoted = self.server.calls[candidate_count:]
        self.assertTrue(all(call[1].startswith(ORIGIN) for call in promoted))
        self.assertTrue(all("X-Serverless-Authorization" not in call[2] for call in promoted))
        self.assertEqual(2, len([call for call in self.server.calls if call[0] == "PUT"]))
        self.assertEqual(3, len([call for call in self.server.calls if call[1].endswith("/merge")]))
        self.assertEqual(prepared["accountId"], json.loads(self.state_file.read_text())["accountId"])
        self.assertEqual("legacy", prepared["journalMergeSmoke"]["storageMode"])
        self.assertFalse(any("/xrpc/" in call[1] for call in self.server.calls))

    def space_server(self, **overrides):
        server = space_fake.PersonalSpaceServer(ACCOUNT, "smoketest_fixture", ORIGIN, **overrides)
        self.server = server
        return server

    def test_space_account_merges_through_its_personal_space_and_never_calls_legacy_routes(self):
        server = self.space_server()
        self.probe()
        prepared = json.loads(self.state_file.read_text())["journalMergeSmoke"]
        self.assertEqual("personalSpaceV1", prepared["storageMode"])
        self.assertIn("mergedCid", prepared)
        self.probe("verify")
        self.assertEqual([], server.legacy_calls)
        allowed = tuple("/api/v1" + path for path in space_fake.ACCOUNT_ROUTES)
        for _method, url, _headers, _body in server.calls:
            path = url.removeprefix(CANDIDATE).removeprefix(ORIGIN)
            self.assertTrue(path.startswith("/xrpc/app.logdate.space.") or path.startswith(allowed), path)
        source, destination = prepared["source"]["rkey"], prepared["destination"]["rkey"]
        self.assertTrue(server.records[source]["deleted"])
        self.assertEqual(prepared["mergedCid"], server.records[destination]["cid"])
        writes = [call for call in server.calls if call[1].endswith("putRecord")]
        self.assertEqual(6, len(writes))

    def test_space_merge_fails_closed_on_each_broken_guarantee(self):
        messages = {
            "swap-ignored": r"POST /app.logdate.space.putRecord returned HTTP 200",
            "value": "Space record does not match what was written",
            "tombstone": "tombstone is missing",
            "deleted-readable": r"GET /app.logdate.space.getRecord returned HTTP 200",
        }
        for corruption, message in messages.items():
            with self.subTest(corruption=corruption):
                self.state.pop("journalMergeSmoke", None)
                self.write_state()
                self.space_server().corruption = corruption
                with self.assertRaisesRegex(smoke.SmokeError, message):
                    self.probe()

    def test_unknown_or_missing_storage_mode_and_missing_did_fail_before_any_write(self):
        cases = (
            ({"mode": "personalSpaceV2"}, "storageMode 'personalSpaceV2' is not covered by this smoke test"),
            ({"mode": None}, "did not report a storageMode"),
            ({"did": None}, "did not report a DID"),
            ({"features": ["deviceLinkingV1"]}, "does not advertise personalSpaceV1"),
        )
        for overrides, message in cases:
            with self.subTest(overrides=overrides):
                server = self.space_server(**overrides)
                with self.assertRaisesRegex(smoke.SmokeError, message):
                    self.probe()
                self.assertFalse(any(call[0] != "GET" for call in server.calls))
                self.assertEqual([], server.legacy_calls)

    def test_a_changed_storage_mode_fails_the_promoted_proof(self):
        self.space_server()
        self.probe()
        self.server = JournalServer()
        with self.assertRaisesRegex(smoke.SmokeError, "storageMode changed since prepare"):
            self.probe("verify")

    def test_missing_capability_fails_before_any_fixture_write(self):
        self.server.features = []
        with self.assertRaisesRegex(smoke.SmokeError, "journalMergeV1"):
            self.probe()
        self.assertFalse(any(call[0] != "GET" for call in self.server.calls))

    def test_account_mismatch_fails_before_any_fixture_write(self):
        self.server.account = str(uuid.uuid4())
        with self.assertRaisesRegex(smoke.SmokeError, "identity"):
            self.probe()
        self.assertFalse(any(call[0] != "GET" for call in self.server.calls))

    def test_insecure_state_is_rejected_without_network(self):
        self.state_file.chmod(0o644)
        with self.assertRaisesRegex(smoke.SmokeError, "0600"):
            self.probe()
        self.assertEqual([], self.server.calls)

    def test_unbound_origin_is_rejected_without_network(self):
        with self.assertRaisesRegex(smoke.SmokeError, "origin"):
            smoke.run_probe("https://wrong.example.test", self.state_file, "verify", transport=self.server.request)
        self.assertEqual([], self.server.calls)

    def test_each_required_merge_invariant_fails_closed(self):
        messages = {"retry": "retry", "metadata": "metadata", "tombstone": "tombstone", "membership": "memberships"}
        for corruption, message in messages.items():
            with self.subTest(corruption=corruption):
                self.state.pop("journalMergeSmoke", None)
                self.write_state()
                self.server = JournalServer()
                self.server.corruption = corruption
                with self.assertRaisesRegex(smoke.SmokeError, message):
                    self.probe()

    def test_promoted_missing_fixture_does_not_create_new_data(self):
        with self.assertRaisesRegex(smoke.SmokeError, "prepared"):
            self.probe("verify")
        self.assertFalse(any(call[0] != "GET" for call in self.server.calls))

    def test_plain_text_not_found_is_accepted_only_as_an_expected_status(self):
        network = Network(Response(404, b"Not Found", request_id="app-request"))
        self.assertEqual((404, {}, "app-request"), network.request("GET", CANDIDATE + "/api/v1/journals/missing"))

        with patch_transport(self.server, (404, {}, "app-request")):
            probe = smoke.Probe(CANDIDATE, self.state, self.server.request, None)
            with self.assertRaisesRegex(smoke.SmokeError, r"GET /journals/missing returned HTTP 404 \(request app-request\)"):
                probe.request("GET", "/journals/missing")

    def test_recovery_marks_only_bound_private_state_for_existing_account_cleanup(self):
        smoke.run_probe(CANDIDATE, self.state_file, "recover", transport=self.server.request)
        recovered = json.loads(self.state_file.read_text())
        self.assertTrue(recovered["recoveryMode"])
        self.assertEqual("existing-media", recovered["mediaId"])
        self.assertEqual(ACCOUNT, recovered["accountId"])
        self.assertEqual([], self.server.calls)

    def test_rollout_requires_candidate_and_public_merge_proofs_around_promotion(self):
        workflow = (ROOT / ".github/workflows/deploy-server-cloud-run.yml").read_text()
        self.assertIn("python3 scripts/journal-merge-smoke.py", workflow)
        candidate = workflow.index("python3 scripts/journal-merge-smoke.py")
        promotion = workflow.index("- name: Promote candidate")
        public = workflow.index("python3 scripts/journal-merge-smoke.py", promotion)
        cleanup = workflow.index("--phase verify-and-cleanup", promotion)
        self.assertLess(candidate, promotion)
        self.assertLess(promotion, public)
        self.assertLess(public, cleanup)
        self.assertIn('--private-service-token-file "$INVOKER_TOKEN_FILE"', workflow[candidate:promotion])
        self.assertIn('--phase recover', workflow[candidate:promotion])
        self.assertIn('test "$journal_merge_status" -eq 0', workflow[cleanup:])


if __name__ == "__main__":
    unittest.main()
