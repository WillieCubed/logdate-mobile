#!/usr/bin/env python3
"""The identity backfill job reuses the server's pinned runtime inputs with its own entrypoint."""
import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]

CONTRACT = {
    "project_id": "project-test", "region": "us-central1", "service_name": "server-test",
    "image": "registry/server@sha256:123", "runtime_service_account": "runtime@project-test.iam.gserviceaccount.com",
    "env_vars": {"ATPROTO_PLC_PUBLISH_ENABLED": "false"},
    "secret_env": {"DATABASE_URL": {"secret_id": "database-url", "version": "17"}},
    "runtime": {"resources": {"cpu": "1", "memory": "512Mi"}},
}


def load_module():
    spec = importlib.util.spec_from_file_location("identity_backfill_job", ROOT / "scripts/identity-backfill-job.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def container(job):
    return job["spec"]["template"]["spec"]["template"]["spec"]["containers"][0]


class IdentityBackfillJobTest(unittest.TestCase):
    def test_dry_run_only_counts(self):
        job = load_module().render_job(CONTRACT, dry_run=True, allow_plc_publishing=False)
        self.assertEqual("server-test-identity-backfill", job["metadata"]["name"])
        self.assertEqual(["--backfill-identities", "--dry-run"], container(job)["args"])
        self.assertEqual(CONTRACT["image"], container(job)["image"])
        self.assertIn({"name": "DATABASE_URL", "valueFrom": {"secretKeyRef": {"name": "database-url", "key": "17"}}},
                      container(job)["env"])

    def test_publishing_needs_the_explicit_flag(self):
        module = load_module()
        self.assertEqual(["--backfill-identities"],
                         container(module.render_job(CONTRACT, dry_run=False, allow_plc_publishing=False))["args"])
        self.assertEqual(["--backfill-identities", "--allow-plc-publishing"],
                         container(module.render_job(CONTRACT, dry_run=False, allow_plc_publishing=True))["args"])


if __name__ == "__main__":
    unittest.main()
