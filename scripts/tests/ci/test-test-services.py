#!/usr/bin/env python3
"""Exercise registry failure, readiness, and cleanup without a Docker daemon."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / "scripts/ci/start-test-services.py"
POSTGRES = "721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea"


class TestServices(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.folder = Path(self.directory.name)
        self.log = self.folder / "docker.jsonl"
        docker = self.folder / "docker"
        docker.write_text("#!" + sys.executable + "\n" + '''
import json, os, pathlib, sys
args = sys.argv[1:]
with open(os.environ["MOCK_LOG"], "a") as log:
    log.write(json.dumps(args) + "\\n")
state = pathlib.Path(os.environ["MOCK_STATE"])
names = json.loads(state.read_text()) if state.exists() else []
if args[0] == "pull":
    mode = os.environ.get("MOCK_PULL", "mirror-fails")
    sys.exit(1 if mode == "all-fail" or (mode == "mirror-fails" and args[1].startswith("public.ecr.aws/")) else 0)
if args[0] == "run":
    names.append(args[args.index("--name") + 1])
    state.write_text(json.dumps(names))
    print("container-id")
elif args[0] == "inspect":
    if args[-1] not in names and os.environ.get("MOCK_FOREIGN") != "true":
        sys.exit(1)
    if "Labels" in args[2]:
        print("foreign" if os.environ.get("MOCK_FOREIGN") == "true" else "123:1:test")
    else:
        print(os.environ.get("MOCK_HEALTH", "healthy"))
''')
        docker.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.folder) + os.pathsep + os.environ["PATH"],
                        MOCK_LOG=str(self.log), MOCK_STATE=str(self.folder / "state.json"),
                        GITHUB_ACTIONS="true", GITHUB_RUN_ID="123", GITHUB_RUN_ATTEMPT="1", GITHUB_JOB="test")

    def invoke(self, action="start", mode="e2e", **overrides):
        return subprocess.run([sys.executable, str(SCRIPT), action, mode],
                              env=self.env | overrides, capture_output=True, text=True)

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def test_fallback_keeps_the_pinned_image_and_database_configuration(self):
        result = self.invoke()
        self.assertEqual(0, result.returncode, result.stderr)
        pulls = [call[1] for call in self.calls() if call[0] == "pull"]
        self.assertEqual(["public.ecr.aws/docker/library/postgres:16-alpine@sha256:" + POSTGRES,
                          "docker.io/library/postgres:16-alpine@sha256:" + POSTGRES], pulls)
        run = next(call for call in self.calls() if call[0] == "run")
        self.assertIn("POSTGRES_DB=logdate", run)
        self.assertIn("POSTGRES_USER=logdate", run)
        self.assertIn("POSTGRES_PASSWORD=logdate", run)
        self.assertIn("5432:5432", run)
        self.assertEqual(pulls[-1], run[-1])

    def test_available_mirror_does_not_pull_from_another_registry(self):
        result = self.invoke(MOCK_PULL="success")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, sum(call[0] == "pull" for call in self.calls()))

    def test_unit_tests_receive_postgres_and_redis(self):
        result = self.invoke(mode="unit")
        self.assertEqual(0, result.returncode, result.stderr)
        runs = [call for call in self.calls() if call[0] == "run"]
        self.assertEqual(2, len(runs))
        self.assertIn("POSTGRES_DB=logdate_test", runs[0])
        self.assertIn("6379:6379", runs[1])

    def test_registry_failure_cannot_start_a_mutable_replacement_image(self):
        result = self.invoke(MOCK_PULL="all-fail")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(call[0] == "run" for call in self.calls()))

    def test_unhealthy_service_fails_and_cleans_up_its_container(self):
        result = self.invoke(MOCK_HEALTH="unhealthy")
        self.assertNotEqual(0, result.returncode)
        self.assertTrue(any(call[0] == "rm" for call in self.calls()))

    def test_cleanup_refuses_a_container_from_another_run(self):
        result = self.invoke(action="stop", MOCK_FOREIGN="true")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(any(call[0] == "rm" for call in self.calls()))

    def test_cleanup_removes_the_current_job_containers_and_their_temporary_volumes(self):
        self.assertEqual(0, self.invoke(mode="unit").returncode)
        result = self.invoke(action="stop", mode="unit")
        self.assertEqual(0, result.returncode, result.stderr)
        removals = [call for call in self.calls() if call[0] == "rm"]
        self.assertEqual({"logdate-ci-postgres-123-1-test", "logdate-ci-redis-123-1-test"},
                         {call[-1] for call in removals})
        self.assertTrue(all("--volumes" in call for call in removals))

    def test_local_invocation_cannot_start_or_remove_services(self):
        result = self.invoke(GITHUB_ACTIONS="false")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], self.calls())


if __name__ == "__main__":
    unittest.main()
