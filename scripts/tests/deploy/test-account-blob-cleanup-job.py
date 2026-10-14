#!/usr/bin/env python3
"""The scheduled job uses the same pinned runtime inputs, without copying secret values."""
import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]


class CleanupJobTest(unittest.TestCase):
    def test_job_uses_image_entrypoint_and_exact_secret_references(self):
        spec = importlib.util.spec_from_file_location("cleanup_job", ROOT / "scripts/account-blob-cleanup-job.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        contract = {
            "project_id": "project-test", "region": "us-central1", "service_name": "server-test",
            "image": "registry/server@sha256:123", "runtime_service_account": "runtime@project-test.iam.gserviceaccount.com",
            "env_vars": {"GCS_BUCKET_NAME": "media", "AUTO_MIGRATE": "false"},
            "secret_env": {"DATABASE_URL": {"secret_id": "database-url", "version": "17"}},
            "runtime": {"resources": {"cpu": "1", "memory": "512Mi"}},
        }
        job = module.render_job(contract)
        template = job["spec"]["template"]["spec"]["template"]["spec"]
        container = template["containers"][0]
        self.assertNotIn("command", container)
        self.assertEqual(["--cleanup-account-blobs"], container["args"])
        self.assertEqual(contract["image"], container["image"])
        self.assertEqual(contract["runtime_service_account"], template["serviceAccountName"])
        self.assertIn({"name": "DATABASE_URL", "valueFrom": {"secretKeyRef": {"name": "database-url", "key": "17"}}}, container["env"])
        self.assertEqual("3600", template["timeoutSeconds"])
        # The durable queue owns backoff; an immediate platform retry would see no due work
        # and turn a failed cleanup execution into a misleading success.
        self.assertEqual(0, template["maxRetries"])


if __name__ == "__main__":
    unittest.main()
