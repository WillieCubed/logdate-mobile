#!/usr/bin/env python3
"""The event stream service runs the main service's contract over HTTP/1 and must accept a WebSocket."""
import importlib.util
import pathlib
import socket
import threading
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("repo_event_stream_service", ROOT / "scripts/repo-event-stream-service.py")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

CONTRACT = {
    "project_id": "project-test",
    "region": "us-central1",
    "service_name": "server-test",
    "image": "registry/server@sha256:123",
    "runtime_service_account": "runtime@project-test.iam.gserviceaccount.com",
    "env_vars": {"ATPROTO_FIREHOSE_ENABLED": "true", "AUTO_MIGRATE": "false"},
    "secret_env": {"DATABASE_URL": {"secret_id": "database-url", "version": "17"}},
    "repo_event_stream": {"service_name": "server-test-repo-events", "timeout_seconds": 3600},
    "runtime": {
        "port": 8080,
        "request_concurrency": 80,
        "scaling": {"min_instances": 0, "max_instances": 10},
        "resources": {"cpu": "1", "memory": "512Mi", "cpu_idle": True, "startup_cpu_boost": True},
        "startup_probe": {"path": "/health", "port": 8080, "timeout_seconds": 5, "period_seconds": 5, "failure_threshold": 12},
        "liveness_probe": {
            "path": "/health", "port": 8080, "initial_delay_seconds": 15, "timeout_seconds": 5,
            "period_seconds": 30, "failure_threshold": 3,
        },
    },
}


class OneResponseServer:
    """Answers one connection with a fixed status line and records the request it read."""

    def __init__(self, status_line):
        self.status_line = status_line
        self.request = b""
        self.listener = socket.create_server(("127.0.0.1", 0))
        self.url = f"http://127.0.0.1:{self.listener.getsockname()[1]}"
        self.thread = threading.Thread(target=self.serve, daemon=True)
        self.thread.start()

    def serve(self):
        connection, _ = self.listener.accept()
        with connection:
            while b"\r\n\r\n" not in self.request:
                self.request += connection.recv(4096)
            connection.sendall(self.status_line + b"\r\n\r\n")
        self.listener.close()


class RepoEventStreamServiceTest(unittest.TestCase):
    def test_service_runs_the_contract_over_http1_with_the_long_stream_timeout(self):
        service = MODULE.render_service(CONTRACT)
        template = service["spec"]["template"]
        container = template["spec"]["containers"][0]
        self.assertEqual("server-test-repo-events", service["metadata"]["name"])
        self.assertEqual("project-test", service["metadata"]["namespace"])
        self.assertEqual([{"name": "http1", "containerPort": 8080}], container["ports"])
        self.assertEqual(CONTRACT["image"], container["image"])
        self.assertEqual(3600, template["spec"]["timeoutSeconds"])
        self.assertEqual(CONTRACT["runtime_service_account"], template["spec"]["serviceAccountName"])
        self.assertEqual(80, template["spec"]["containerConcurrency"])
        self.assertIn({"name": "ATPROTO_FIREHOSE_ENABLED", "value": "true"}, container["env"])
        self.assertIn({"name": "DATABASE_URL", "valueFrom": {"secretKeyRef": {"name": "database-url", "key": "17"}}}, container["env"])
        self.assertEqual({"cpu": "1", "memory": "512Mi"}, container["resources"]["limits"])
        self.assertEqual("/health", container["startupProbe"]["httpGet"]["path"])
        self.assertEqual(12, container["startupProbe"]["failureThreshold"])
        self.assertEqual(15, container["livenessProbe"]["initialDelaySeconds"])
        self.assertEqual("all", service["metadata"]["annotations"]["run.googleapis.com/ingress"])
        annotations = template["metadata"]["annotations"]
        self.assertEqual("0", annotations["autoscaling.knative.dev/minScale"])
        self.assertEqual("10", annotations["autoscaling.knative.dev/maxScale"])
        self.assertEqual("true", annotations["run.googleapis.com/cpu-throttling"])
        self.assertEqual([{"latestRevision": True, "percent": 100}], service["spec"]["traffic"])

    def test_handshake_reports_an_accepted_upgrade(self):
        server = OneResponseServer(b"HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade")
        self.assertEqual(101, MODULE.handshake_status(server.url))
        request = server.request.decode()
        self.assertTrue(request.startswith("GET /xrpc/com.atproto.sync.subscribeRepos HTTP/1.1\r\n"))
        self.assertIn("\r\nUpgrade: websocket\r\n", request)
        self.assertIn("\r\nSec-WebSocket-Version: 13\r\n", request)

    def test_a_refused_upgrade_stops_the_deploy(self):
        server = OneResponseServer(b"HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0")
        with self.assertRaises(SystemExit) as refused:
            MODULE.require_websocket(server.url, attempts=1, wait_seconds=0)
        self.assertIn("HTTP 405, not 101", str(refused.exception))


if __name__ == "__main__":
    unittest.main()
