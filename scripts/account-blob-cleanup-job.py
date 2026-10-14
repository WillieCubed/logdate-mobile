#!/usr/bin/env python3
"""Deploy the one-shot cleanup image from the immutable server deployment contract."""
import argparse
import json
import pathlib
import subprocess
import tempfile


def render_job(contract):
    env = [{"name": key, "value": value} for key, value in sorted(contract["env_vars"].items())]
    env.extend({"name": key, "valueFrom": {"secretKeyRef": {"name": value["secret_id"], "key": value["version"]}}}
               for key, value in sorted(contract["secret_env"].items()))
    container = {
        "image": contract["image"], "args": ["--cleanup-account-blobs"], "env": env,
        "resources": {"limits": {key: contract["runtime"]["resources"][key] for key in ("cpu", "memory")}},
    }
    return {
        "apiVersion": "run.googleapis.com/v1", "kind": "Job",
        "metadata": {"name": contract["service_name"] + "-account-blob-cleanup", "namespace": contract["project_id"]},
        "spec": {"template": {"spec": {"parallelism": 1, "taskCount": 1, "template": {"spec": {
            "serviceAccountName": contract["runtime_service_account"], "timeoutSeconds": "3600", "maxRetries": 0,
            "containers": [container],
        }}}}},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--contract-file", type=pathlib.Path, required=True)
    args = parser.parse_args()
    contract = json.loads(args.contract_file.read_text())
    job = render_job(contract)
    # This is configuration and exact secret references; no secret contents are fetched.
    with tempfile.TemporaryDirectory(prefix="logdate-cleanup-job-") as directory:
        path = pathlib.Path(directory) / "job.json"
        path.write_text(json.dumps(job))
        path.chmod(0o600)
        subprocess.run(["gcloud", "run", "jobs", "replace", str(path), "--region", contract["region"],
                        "--project", contract["project_id"], "--quiet"], check=True)
    subprocess.run(["gcloud", "run", "jobs", "execute", job["metadata"]["name"], "--wait",
                    "--region", contract["region"], "--project", contract["project_id"], "--quiet"], check=True)


if __name__ == "__main__":
    main()
