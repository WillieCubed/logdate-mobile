#!/usr/bin/env python3
"""Behavioral checks for the deployment journal merge proof."""

import copy
import importlib.util
import json
import tempfile
import unittest
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("journal_merge_smoke", ROOT / "scripts/journal-merge-smoke.py")
smoke = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(smoke)
CANDIDATE = "https://candidate.example.test"
ORIGIN = "https://cloud.example.test"
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
            return 200, {"success": True, "data": {"account": {"id": self.account, "username": "smoketest_fixture"}}}
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
            "accountId": ACCOUNT, "username": "smoketest_fixture", "origin": ORIGIN,
            "expectedRpId": "example.test", "credentialId": "Y3JlZGVudGlhbA", "userHandle": "aGFuZGxl",
        }
        self.credential_file.write_text(json.dumps(credential))
        self.credential_file.chmod(0o600)
        self.state = {
            "format": "logdate-deploy-smoke-v2", "accountId": ACCOUNT, "username": "smoketest_fixture",
            "accessToken": ACCESS, "refreshToken": "refresh-do-not-log", "canonicalOrigin": ORIGIN,
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
