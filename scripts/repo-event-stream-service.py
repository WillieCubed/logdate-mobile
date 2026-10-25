#!/usr/bin/env python3
"""Deploy the repository event stream's HTTP/1 service from the immutable server deployment contract.

The main service runs end-to-end HTTP/2 so uploads over Cloud Run's 32 MiB HTTP/1 request limit reach
it, and Cloud Run does not pass WebSockets to such a service. The stream, a WebSocket, therefore runs
from the same image and contract as a second service over HTTP/1, and the load balancer in
infra/terraform sends com.atproto.sync.subscribeRepos to it.
"""
import argparse
import base64
import json
import os
import pathlib
import socket
import ssl
import subprocess
import tempfile
import time
from urllib.parse import urlparse

STREAM_PATH = "/xrpc/com.atproto.sync.subscribeRepos"
HANDSHAKE_ATTEMPTS = 6


def render_service(contract):
    stream = contract["repo_event_stream"]
    runtime = contract["runtime"]
    resources = runtime["resources"]
    env = [{"name": key, "value": value} for key, value in sorted(contract["env_vars"].items())]
    env.extend({"name": key, "valueFrom": {"secretKeyRef": {"name": value["secret_id"], "key": value["version"]}}}
               for key, value in sorted(contract["secret_env"].items()))
    startup = runtime["startup_probe"]
    liveness = runtime["liveness_probe"]
    container = {
        "image": contract["image"],
        # Naming the port http1 is what keeps Cloud Run speaking HTTP/1, which WebSockets need.
        "ports": [{"name": "http1", "containerPort": runtime["port"]}],
        "env": env,
        "resources": {"limits": {"cpu": resources["cpu"], "memory": resources["memory"]}},
        "startupProbe": {
            "httpGet": {"path": startup["path"], "port": startup["port"]},
            "timeoutSeconds": startup["timeout_seconds"],
            "periodSeconds": startup["period_seconds"],
            "failureThreshold": startup["failure_threshold"],
        },
        "livenessProbe": {
            "httpGet": {"path": liveness["path"], "port": liveness["port"]},
            "initialDelaySeconds": liveness["initial_delay_seconds"],
            "timeoutSeconds": liveness["timeout_seconds"],
            "periodSeconds": liveness["period_seconds"],
            "failureThreshold": liveness["failure_threshold"],
        },
    }
    return {
        "apiVersion": "serving.knative.dev/v1",
        "kind": "Service",
        "metadata": {
            "name": stream["service_name"],
            "namespace": contract["project_id"],
            "annotations": {"run.googleapis.com/ingress": "all"},
        },
        "spec": {
            "template": {
                "metadata": {"annotations": {
                    "autoscaling.knative.dev/minScale": str(runtime["scaling"]["min_instances"]),
                    "autoscaling.knative.dev/maxScale": str(runtime["scaling"]["max_instances"]),
                    "run.googleapis.com/cpu-throttling": "true" if resources["cpu_idle"] else "false",
                    "run.googleapis.com/startup-cpu-boost": "true" if resources["startup_cpu_boost"] else "false",
                }},
                "spec": {
                    "serviceAccountName": contract["runtime_service_account"],
                    "containerConcurrency": runtime["request_concurrency"],
                    "timeoutSeconds": stream["timeout_seconds"],
                    "containers": [container],
                },
            },
            "traffic": [{"latestRevision": True, "percent": 100}],
        },
    }


def handshake_status(url, timeout=15.0):
    """Opens a WebSocket handshake to the stream at url and returns the HTTP status it got back."""
    parsed = urlparse(url)
    secure = parsed.scheme == "https"
    port = parsed.port or (443 if secure else 80)
    key = base64.b64encode(os.urandom(16)).decode()
    request = (
        f"GET {STREAM_PATH} HTTP/1.1\r\nHost: {parsed.hostname}\r\nConnection: Upgrade\r\nUpgrade: websocket\r\n"
        f"Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: {key}\r\n\r\n"
    )
    with socket.create_connection((parsed.hostname, port), timeout=timeout) as raw:
        connection = ssl.create_default_context().wrap_socket(raw, server_hostname=parsed.hostname) if secure else raw
        with connection:
            connection.sendall(request.encode())
            status_line = connection.makefile("rb").readline().decode("latin-1")
    parts = status_line.split()
    return int(parts[1]) if len(parts) >= 2 and parts[1].isdigit() else 0


def require_websocket(url, attempts=HANDSHAKE_ATTEMPTS, wait_seconds=5.0):
    status = 0
    for attempt in range(1, attempts + 1):
        try:
            status = handshake_status(url)
        except OSError:
            status = 0
        if status == 101:
            print("The repository event stream service accepts WebSocket connections.")
            return
        if attempt < attempts:
            time.sleep(wait_seconds)
    raise SystemExit(f"The repository event stream service answered the WebSocket handshake with HTTP {status}, not 101.")


def gcloud(*arguments, capture=False):
    result = subprocess.run(["gcloud", *arguments], check=True, text=True, stdout=subprocess.PIPE if capture else None)
    return result.stdout.strip() if capture else None


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--contract-file", type=pathlib.Path, required=True)
    args = parser.parse_args()
    contract = json.loads(args.contract_file.read_text())
    if "repo_event_stream" not in contract:
        print("The repository event stream is off in this contract.")
        return
    service = render_service(contract)
    name = service["metadata"]["name"]
    location = ["--region", contract["region"], "--project", contract["project_id"], "--quiet"]
    # This is configuration and exact secret references; no secret contents are fetched.
    with tempfile.TemporaryDirectory(prefix="logdate-repo-event-stream-") as directory:
        path = pathlib.Path(directory) / "service.json"
        path.write_text(json.dumps(service))
        path.chmod(0o600)
        gcloud("run", "services", "replace", str(path), *location)
    # Relays reach the stream without signing in, like every other public route on the server.
    gcloud("run", "services", "add-iam-policy-binding", name, "--member=allUsers", "--role=roles/run.invoker",
           *location, capture=True)
    url = gcloud("run", "services", "describe", name, "--format=value(status.url)", *location, capture=True)
    require_websocket(url)


if __name__ == "__main__":
    main()
