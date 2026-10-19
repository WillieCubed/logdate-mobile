#!/usr/bin/env python3
"""Run the AT Protocol identity backfill as a one-shot Cloud Run job.

Accounts created before AT Protocol identities existed have no handle or DID until their owner
signs in. This runs the server image with `--backfill-identities` against the environment's own
database and secrets, taken from the immutable deployment contract that
scripts/render-cloud-run-contract.sh produces. Run it with --dry-run first: that only counts the
accounts that need an identity. A real run refuses to start while the server publishes PLC
operations unless --allow-plc-publishing is given, because every new identity is then sent to the
public PLC directory. The job's log shows the counts; see docs/runbook/handle-resolution.md.
"""
import argparse
import importlib.util
import json
import pathlib
import subprocess
import tempfile

SCRIPTS = pathlib.Path(__file__).resolve().parent


def _cleanup_job_module():
    spec = importlib.util.spec_from_file_location("account_blob_cleanup_job", SCRIPTS / "account-blob-cleanup-job.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def render_job(contract, dry_run, allow_plc_publishing):
    """The cleanup job's runtime inputs, with the backfill entrypoint and its own job name."""
    job = _cleanup_job_module().render_job(contract)
    args = ["--backfill-identities"]
    if dry_run:
        args.append("--dry-run")
    if allow_plc_publishing:
        args.append("--allow-plc-publishing")
    job["metadata"]["name"] = contract["service_name"] + "-identity-backfill"
    job["spec"]["template"]["spec"]["template"]["spec"]["containers"][0]["args"] = args
    return job


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--contract-file", type=pathlib.Path, required=True)
    parser.add_argument("--dry-run", action="store_true", help="Only count the accounts that need an identity.")
    parser.add_argument("--allow-plc-publishing", action="store_true",
                        help="Allow a run while the server publishes PLC operations to the public directory.")
    args = parser.parse_args()
    if args.dry_run and args.allow_plc_publishing:
        parser.error("--allow-plc-publishing has no effect on a dry run")
    contract = json.loads(args.contract_file.read_text())
    job = render_job(contract, args.dry_run, args.allow_plc_publishing)
    # This is configuration and exact secret references; no secret contents are fetched.
    with tempfile.TemporaryDirectory(prefix="logdate-identity-backfill-") as directory:
        path = pathlib.Path(directory) / "job.json"
        path.write_text(json.dumps(job))
        path.chmod(0o600)
        subprocess.run(["gcloud", "run", "jobs", "replace", str(path), "--region", contract["region"],
                        "--project", contract["project_id"], "--quiet"], check=True)
    subprocess.run(["gcloud", "run", "jobs", "execute", job["metadata"]["name"], "--wait",
                    "--region", contract["region"], "--project", contract["project_id"], "--quiet"], check=True)


if __name__ == "__main__":
    main()
