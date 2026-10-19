#!/usr/bin/env python3
"""The identity publishing job runs the server command with the deployed contract and refuses unsafe publishes."""
import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]


def load():
    spec = importlib.util.spec_from_file_location("identity_publishing_job", ROOT / "scripts/identity-publishing-job.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def contract(**env):
    return {
        "project_id": "project-test", "region": "us-central1", "service_name": "server-test",
        "image": "registry/server@sha256:123", "runtime_service_account": "runtime@project-test.iam.gserviceaccount.com",
        "env_vars": {"AUTO_MIGRATE": "false", **env},
        "secret_env": {"DATABASE_URL": {"secret_id": "database-url", "version": "17"}},
        "runtime": {"resources": {"cpu": "1", "memory": "512Mi"}},
    }


class IdentityPublishingJobTest(unittest.TestCase):
    def setUp(self):
        self.job = load()

    def test_status_job_runs_the_command_with_the_contract_image_and_secrets(self):
        args = self.job.parse_args(["status", "--contract-file", "contract.json", "--handle", "alice.logdate.app", "--limit", "5"])
        rendered = self.job.render_job(contract(), args)
        task = rendered["spec"]["template"]["spec"]["template"]["spec"]
        container = task["containers"][0]
        self.assertEqual("server-test-identity-publishing", rendered["metadata"]["name"])
        self.assertEqual(["--identity-publishing", "status", "--handle", "alice.logdate.app", "--limit", "5"], container["args"])
        self.assertEqual("registry/server@sha256:123", container["image"])
        self.assertIn({"name": "DATABASE_URL", "valueFrom": {"secretKeyRef": {"name": "database-url", "key": "17"}}}, container["env"])
        # A failed publish needs an operator to read the outcome first, never an automatic retry.
        self.assertEqual(0, task["maxRetries"])

    def test_publish_passes_the_confirmed_directory_and_limits(self):
        args = self.job.parse_args([
            "publish", "--contract-file", "c.json", "--confirm-directory", "https://plc.directory",
            "--batch-size", "10", "--operations-per-minute", "20", "--max-failures", "2", "--reconcile",
        ])
        self.job.check_contract(contract(ATPROTO_PLC_PUBLISH_ENABLED="true"), args)
        self.assertEqual(
            ["--identity-publishing", "publish", "--confirm-directory", "https://plc.directory", "--reconcile",
             "--batch-size", "10", "--operations-per-minute", "20", "--max-failures", "2"],
            self.job.command_args(args),
        )

    def test_publish_is_refused_unless_the_contract_turned_publishing_on(self):
        args = self.job.parse_args(["publish", "--contract-file", "c.json", "--confirm-directory", "https://plc.directory"])
        with self.assertRaises(SystemExit):
            self.job.check_contract(contract(), args)
        with self.assertRaises(SystemExit):
            self.job.check_contract(contract(ATPROTO_PLC_PUBLISH_ENABLED="false"), args)

    def test_publish_is_refused_when_the_confirmed_directory_is_not_the_contracts(self):
        args = self.job.parse_args(["publish", "--contract-file", "c.json", "--confirm-directory", "https://plc.example.com"])
        with self.assertRaises(SystemExit):
            self.job.check_contract(contract(ATPROTO_PLC_PUBLISH_ENABLED="true"), args)

    def test_read_only_modes_never_take_a_directory_or_pacing(self):
        for argv in (
            ["publish", "--contract-file", "c.json"],
            ["status", "--contract-file", "c.json", "--confirm-directory", "https://plc.directory"],
            ["status", "--contract-file", "c.json", "--reconcile"],
            ["dry-run", "--contract-file", "c.json", "--operations-per-minute", "5"],
        ):
            with self.assertRaises(SystemExit, msg=f"accepted {argv}"):
                self.job.parse_args(argv)
        dry_run = self.job.parse_args(["dry-run", "--contract-file", "c.json", "--reconcile"])
        self.assertEqual(["--identity-publishing", "publish", "--dry-run", "--reconcile"], self.job.command_args(dry_run))


if __name__ == "__main__":
    unittest.main()
