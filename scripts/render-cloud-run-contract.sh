#!/usr/bin/env bash
# Render a deterministic, deployment-ready Cloud Run contract from committed Terraform inputs.

set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
SCRIPT_PATH="$SCRIPT_DIR/${BASH_SOURCE[0]##*/}"
REPO_CANDIDATE="$(cd "$SCRIPT_DIR/.." && pwd -P)"
ENVIRONMENT=""
RELEASE_SHA=""
PRIVATE_WORK_DIR=""

die() {
    printf 'ERROR: %s\n' "$1" >&2
    exit 1
}

cleanup() {
    if [[ -n "$PRIVATE_WORK_DIR" ]]; then
        rm -rf "$PRIVATE_WORK_DIR"
    fi
}
trap cleanup EXIT

while [[ $# -gt 0 ]]; do
    case "$1" in
        --environment)
            [[ $# -ge 2 ]] || die "--environment requires a value."
            ENVIRONMENT="$2"
            shift 2
            ;;
        --release-sha)
            [[ $# -ge 2 ]] || die "--release-sha requires a value."
            RELEASE_SHA="$2"
            shift 2
            ;;
        *)
            die "unknown argument: $1"
            ;;
    esac
done

[[ "$ENVIRONMENT" == "staging" || "$ENVIRONMENT" == "production" ]] ||
    die "environment must be staging or production."
[[ "$RELEASE_SHA" =~ ^[0-9a-f]{40}$ ]] ||
    die "release SHA must be 40 lowercase hexadecimal characters."

command -v terraform >/dev/null 2>&1 || die "terraform is required."
command -v jq >/dev/null 2>&1 || die "jq is required."
command -v python3 >/dev/null 2>&1 || die "python3 is required."
command -v git >/dev/null 2>&1 || die "git is required."
command -v cmp >/dev/null 2>&1 || die "cmp is required."

while IFS= read -r environment_name; do
    case "$environment_name" in
        TF_* | GIT_*) unset "$environment_name" ;;
    esac
done < <(compgen -e)
unset TF_WORKSPACE TF_LOG TF_LOG_PATH
export TF_INPUT=0

REPO_ROOT="$(git -C "$REPO_CANDIDATE" rev-parse --show-toplevel 2>/dev/null)" ||
    die "renderer must execute from a Git repository."
[[ "$SCRIPT_PATH" == "$REPO_ROOT/scripts/render-cloud-run-contract.sh" ]] ||
    die "renderer must execute from its canonical repository path."
[[ "$(git -C "$REPO_ROOT" cat-file -t "$RELEASE_SHA" 2>/dev/null || true)" == "commit" ]] ||
    die "release SHA must resolve to a commit."

PRIVATE_WORK_DIR="$(mktemp -d)"
chmod 700 "$PRIVATE_WORK_DIR"
ISOLATED_TF_DATA_DIR="$PRIVATE_WORK_DIR/terraform-data"
PRIVATE_CONFIG_DIR="$PRIVATE_WORK_DIR/config"
CONSOLE_STATE="$PRIVATE_WORK_DIR/terraform-console.tfstate"
RAW_CONSOLE_OUTPUT="$PRIVATE_WORK_DIR/terraform-console.out"
SOURCE_JSON="$PRIVATE_WORK_DIR/source.json"
VALIDATED_JSON="$PRIVATE_WORK_DIR/validated.json"
SORTED_JSON="$PRIVATE_WORK_DIR/sorted.json"
TERRAFORM_INIT_LOG="$PRIVATE_WORK_DIR/terraform-init.log"
TERRAFORM_CONSOLE_LOG="$PRIVATE_WORK_DIR/terraform-console.log"
JQ_SORT_LOG="$PRIVATE_WORK_DIR/jq-sort.log"
COMMITTED_RENDERER="$PRIVATE_WORK_DIR/committed-renderer.sh"
mkdir -p "$ISOLATED_TF_DATA_DIR" "$PRIVATE_CONFIG_DIR"
chmod 700 "$ISOLATED_TF_DATA_DIR" "$PRIVATE_CONFIG_DIR"

export TF_DATA_DIR="$ISOLATED_TF_DATA_DIR"

git -C "$REPO_ROOT" show "$RELEASE_SHA:scripts/render-cloud-run-contract.sh" >"$COMMITTED_RENDERER" 2>/dev/null ||
    die "release commit does not contain the renderer."
cmp -s "$SCRIPT_PATH" "$COMMITTED_RENDERER" ||
    die "executed renderer does not match the release commit."

COMMITTED_TERRAFORM_PATHS="$(
    git -C "$REPO_ROOT" ls-tree -r --name-only "$RELEASE_SHA" -- infra/terraform 2>/dev/null |
        while IFS= read -r committed_path; do
            if [[ "$committed_path" =~ ^infra/terraform/[^/]+\.tf$ ]]; then
                printf '%s\n' "$committed_path"
            fi
        done
)" || die "could not enumerate committed Terraform configuration."
[[ -n "$COMMITTED_TERRAFORM_PATHS" ]] || die "release commit contains no Terraform configuration."
while IFS= read -r committed_path; do
    git -C "$REPO_ROOT" show "$RELEASE_SHA:$committed_path" >"$PRIVATE_CONFIG_DIR/${committed_path##*/}" 2>/dev/null ||
        die "could not isolate committed Terraform configuration."
done <<<"$COMMITTED_TERRAFORM_PATHS"

SELECTED_TFVARS="infra/terraform/${ENVIRONMENT}.tfvars"
git -C "$REPO_ROOT" show "$RELEASE_SHA:$SELECTED_TFVARS" >"$PRIVATE_CONFIG_DIR/${ENVIRONMENT}.tfvars" 2>/dev/null ||
    die "release commit does not contain selected Terraform variables."
git -C "$REPO_ROOT" show "$RELEASE_SHA:infra/terraform/.terraform.lock.hcl" >"$PRIVATE_CONFIG_DIR/.terraform.lock.hcl" 2>/dev/null ||
    die "release commit does not contain the Terraform dependency lockfile."

terraform -chdir="$PRIVATE_CONFIG_DIR" init -backend=false -input=false >"$TERRAFORM_INIT_LOG" 2>&1 ||
    die "Terraform backend-free initialization failed."

read -r -d '' TERRAFORM_EXPRESSION <<'EOF' || true
jsonencode({
  project_id                    = var.project_id,
  region                        = var.region,
  service_name                  = var.service_name,
  cloud_run_image               = var.cloud_run_image,
  runtime_service_account_name  = var.runtime_service_account_name,
  artifact_registry_repo        = var.artifact_registry_repo,
  artifact_registry_image_name  = var.artifact_registry_image_name,
  domains                       = var.domains,
  domain                        = var.domain,
  android_signing_certificates = {
    for role, certificate in var.android_signing_certificates :
    role => certificate
    if certificate != null
  },
  env_vars = merge(
    local.base_env,
    var.create_cloud_sql_instance ? {
      INSTANCE_CONNECTION_NAME = "${var.project_id}:${var.region}:${var.cloud_sql_instance_name}",
      DB_NAME                  = var.cloud_sql_database_name,
    } : {},
    local.bucket_env,
    local.webauthn_env,
    local.webauthn_origin_env,
    var.cloud_run_env,
  ),
  secret_env = {
    for name, binding in var.cloud_run_secret_env :
    name => {
      secret_id = binding.secret_id,
      version   = binding.version,
    }
  },
  runtime = {
    allow_unauthenticated = var.allow_unauthenticated,
    ingress               = var.ingress,
    port                  = 8080,
    scaling = {
      min_instances = var.min_instances,
      max_instances = var.max_instances,
    },
    resources = {
      cpu               = var.cpu,
      memory            = var.memory,
      cpu_idle          = var.cpu_idle,
      startup_cpu_boost = var.startup_cpu_boost,
    },
    timeout_seconds     = var.timeout_seconds,
    request_concurrency = var.request_concurrency,
    startup_probe = {
      path              = "/health",
      port              = 8080,
      timeout_seconds   = 5,
      period_seconds    = 5,
      failure_threshold = 12,
    },
    liveness_probe = {
      path                  = "/health",
      port                  = 8080,
      initial_delay_seconds = 15,
      timeout_seconds       = 5,
      period_seconds        = 30,
      failure_threshold     = 3,
    },
  },
})
EOF

if ! printf '%s\n' "$TERRAFORM_EXPRESSION" | tr '\n' ' ' |
    terraform -chdir="$PRIVATE_CONFIG_DIR" console \
        -state="$CONSOLE_STATE" \
        -var-file="${ENVIRONMENT}.tfvars" >"$RAW_CONSOLE_OUTPUT" 2>"$TERRAFORM_CONSOLE_LOG"; then
    die "Terraform console failed."
fi

if ! jq -er 'if type == "string" then . else error("expected one encoded JSON string") end' \
    "$RAW_CONSOLE_OUTPUT" >"$SOURCE_JSON" 2>/dev/null; then
    die "Terraform console did not return one concrete JSON object."
fi

if ! python3 - "$ENVIRONMENT" "$RELEASE_SHA" "$SOURCE_JSON" "$VALIDATED_JSON" <<'PY'
import base64
import json
import pathlib
import re
import sys
from urllib.parse import urlparse

environment, release_sha, source_path, output_path = sys.argv[1:]


class ContractError(Exception):
    pass


def fail(message: str) -> None:
    raise ContractError(message)


def exact_keys(value: object, expected: set[str], label: str) -> dict:
    if not isinstance(value, dict) or set(value) != expected:
        fail(f"{label} must contain exactly: {', '.join(sorted(expected))}")
    return value


def required_string(value: object, label: str) -> str:
    if not isinstance(value, str) or not value.strip() or any(character in value for character in "\r\n\t"):
        fail(f"{label} must be a non-empty single-line string")
    return value.strip()


def positive_integer(value: object, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        fail(f"{label} must be a positive integer")
    return value


def contains_unknown(value: object) -> bool:
    if isinstance(value, str):
        return "(known after apply)" in value
    if isinstance(value, dict):
        return any(contains_unknown(item) for item in value.values())
    if isinstance(value, list):
        return any(contains_unknown(item) for item in value)
    return False


try:
    try:
        source = json.loads(pathlib.Path(source_path).read_text())
    except (json.JSONDecodeError, OSError):
        fail("Terraform console did not return one concrete JSON object")
    if not isinstance(source, dict) or contains_unknown(source):
        fail("Terraform console did not return one concrete JSON object")

    source_keys = {
        "project_id",
        "region",
        "service_name",
        "cloud_run_image",
        "runtime_service_account_name",
        "artifact_registry_repo",
        "artifact_registry_image_name",
        "domains",
        "domain",
        "android_signing_certificates",
        "env_vars",
        "secret_env",
        "runtime",
    }
    if set(source) != source_keys:
        fail("Terraform source projection contains unexpected keys")

    project_id = required_string(source["project_id"], "project_id")
    region = required_string(source["region"], "region")
    service_name = required_string(source["service_name"], "service_name")
    runtime_service_account_name = required_string(
        source["runtime_service_account_name"], "runtime_service_account_name"
    )
    artifact_registry_repo = required_string(source["artifact_registry_repo"], "artifact_registry_repo")
    artifact_registry_image_name = required_string(
        source["artifact_registry_image_name"], "artifact_registry_image_name"
    )
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,28}[a-z0-9]", project_id):
        fail("project_id must be a valid lowercase GCP project ID")
    if not re.fullmatch(r"[a-z][a-z0-9-]{0,61}[a-z0-9]", runtime_service_account_name):
        fail("runtime_service_account_name must be a valid service account name")
    if artifact_registry_image_name != "logdate-server":
        fail("artifact_registry_image_name must be logdate-server")

    domains = source["domains"]
    expected_domain = "cloud-staging.logdate.app" if environment == "staging" else "cloud.logdate.app"
    if domains != [expected_domain]:
        fail(f"environment domains must contain exactly {expected_domain}")
    legacy_domain = source["domain"]
    if legacy_domain != "":
        fail("legacy domain shim must be blank")
    canonical_domain = expected_domain
    canonical_origin = f"https://{canonical_domain}"

    signing = source["android_signing_certificates"]
    if not isinstance(signing, dict):
        fail("android_signing_certificates must be an object")
    if environment == "staging" and set(signing) != {"staging"}:
        fail("staging requires exactly the staging signing certificate")
    if environment == "production" and set(signing) != {"upload", "play_app_signing"}:
        if "upload" not in signing:
            fail("production requires separately identified Android upload certificate fingerprints and origins")
        fail("production requires exactly upload and play_app_signing signing certificates")

    known_debug_fingerprint = "DF:32:69:D4:DC:C9:C4:FE:72:FE:61:62:A0:F4:E9:EE:5F:04:14:47:DC:B3:8E:F6:A9:25:76:FC:38:90:DB:C7"
    role_order = ["staging"] if environment == "staging" else ["upload", "play_app_signing"]
    fingerprints: list[str] = []
    android_origins: list[str] = []
    for role in role_order:
        certificate = exact_keys(signing[role], {"fingerprint", "apk_key_hash_origin"}, f"{role} signing certificate")
        fingerprint = required_string(certificate["fingerprint"], f"{role} certificate fingerprint").upper()
        origin = required_string(certificate["apk_key_hash_origin"], f"{role} apk-key-hash origin")
        if not re.fullmatch(r"(?:[0-9A-F]{2}:){31}[0-9A-F]{2}", fingerprint):
            fail("Android certificate fingerprint must be colon-hex SHA-256")
        digest = bytes.fromhex(fingerprint.replace(":", ""))
        expected_origin = "android:apk-key-hash:" + base64.urlsafe_b64encode(digest).decode().rstrip("=")
        if origin != expected_origin:
            fail("Android certificate fingerprints and apk-key-hash origins must match exactly")
        is_ascending = all(next_byte == (byte + 1) % 256 for byte, next_byte in zip(digest, digest[1:]))
        is_descending = all(next_byte == (byte - 1) % 256 for byte, next_byte in zip(digest, digest[1:]))
        is_placeholder = len(set(digest)) == 1 or is_ascending or is_descending
        if fingerprint == known_debug_fingerprint:
            if environment == "production":
                fail("production certificate sets may not contain the known debug certificate")
            fail("staging certificate set may not contain the known debug certificate")
        if is_placeholder:
            if environment == "production":
                fail("production certificate sets may not contain placeholder certificates")
            fail("staging certificate set may not contain placeholder certificates")
        fingerprints.append(fingerprint)
        android_origins.append(origin)
    if len(fingerprints) != len(set(fingerprints)) or len(android_origins) != len(set(android_origins)):
        fail("production upload and Play app-signing certificates must be distinct")

    env_vars = source["env_vars"]
    if not isinstance(env_vars, dict):
        fail("env_vars must be an object")
    reserved_android_keys = {"ANDROID_CERT_FINGERPRINTS", "WEBAUTHN_ALLOWED_ORIGINS"}
    if reserved_android_keys.intersection(env_vars):
        fail("cloud_run_env must not set reserved Android aggregate keys")
    required_env_keys = {
        "HOST",
        "GCS_PROJECT_ID",
        "GCS_BUCKET_NAME",
        "LOGDATE_ENV",
        "LOGDATE_EXPECT_FIRST_PARTY",
        "LOGDATE_DEPLOYMENT_KIND",
        "LOGDATE_SERVER_DISPLAY_NAME",
        "LOGDATE_PUBLIC_ORIGIN",
        "ATPROTO_PDS_SERVICE_URL",
        "ATPROTO_HANDLE_DOMAIN",
        "WEBAUTHN_RP_ID",
        "WEBAUTHN_ORIGIN",
        "BILLING_PROVIDER",
        "SERVER_ENCRYPTION_ENABLED",
        "SYNC_MEDIA_SIGNED_URLS",
        "SYNC_MEDIA_SIGNED_URL_TTL_HOURS",
        "AUTO_MIGRATE",
    }
    allowed_env_keys = required_env_keys | {"INSTANCE_CONNECTION_NAME", "DB_NAME"}
    unexpected_env = set(env_vars) - allowed_env_keys
    if unexpected_env:
        fail("environment contract contains unexpected keys")
    for key in sorted(required_env_keys):
        if key not in env_vars:
            fail(f"required environment variable {key} is missing")
        required_string(env_vars[key], key)

    expected_values = {
        "HOST": "0.0.0.0",
        "GCS_PROJECT_ID": project_id,
        "LOGDATE_ENV": "production",
        "LOGDATE_EXPECT_FIRST_PARTY": "true",
        "LOGDATE_DEPLOYMENT_KIND": "first_party",
        "LOGDATE_SERVER_DISPLAY_NAME": "LogDate Cloud (Staging)" if environment == "staging" else "LogDate Cloud",
        "LOGDATE_PUBLIC_ORIGIN": canonical_origin,
        "ATPROTO_PDS_SERVICE_URL": canonical_origin,
        "ATPROTO_HANDLE_DOMAIN": canonical_domain if environment == "staging" else "logdate.app",
        "WEBAUTHN_RP_ID": canonical_domain if environment == "staging" else "logdate.app",
        "WEBAUTHN_ORIGIN": canonical_origin,
        "BILLING_PROVIDER": "play",
        "SERVER_ENCRYPTION_ENABLED": "true",
        "SYNC_MEDIA_SIGNED_URLS": "true",
        "SYNC_MEDIA_SIGNED_URL_TTL_HOURS": "1",
        "AUTO_MIGRATE": "false",
    }
    for key, expected in expected_values.items():
        if env_vars[key] != expected:
            if key == "LOGDATE_PUBLIC_ORIGIN":
                fail("LOGDATE_PUBLIC_ORIGIN must be the canonical HTTPS origin")
            if key == "AUTO_MIGRATE":
                fail("AUTO_MIGRATE must be false")
            fail(f"{key} does not match the first-party contract")

    parsed_origin = urlparse(env_vars["LOGDATE_PUBLIC_ORIGIN"])
    if parsed_origin.scheme != "https" or parsed_origin.hostname != canonical_domain or any(
        (parsed_origin.username, parsed_origin.password, parsed_origin.port, parsed_origin.path, parsed_origin.query, parsed_origin.fragment)
    ):
        fail("LOGDATE_PUBLIC_ORIGIN must be the canonical HTTPS origin")

    secret_env = source["secret_env"]
    if not isinstance(secret_env, dict):
        fail("secret_env must be an object")
    required_secret_keys = {
        "DATABASE_USER",
        "DATABASE_PASSWORD",
        "JWT_SECRET",
        "SERVER_ENCRYPTION_KEY",
        "SERVER_ENCRYPTION_KEY_ID",
        "HEALTH_INTERNAL_TOKEN",
    }
    optional_secret_keys = {
        "DATABASE_URL",
        "SENTRY_DSN",
        "GOOGLE_OIDC_CLIENT_IDS",
        "REDIS_URL",
        "STRIPE_SECRET_KEY",
        "STRIPE_WEBHOOK_SECRET",
        "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON",
    }
    if set(secret_env) - required_secret_keys - optional_secret_keys:
        fail("secret contract contains unexpected keys")
    missing_secrets = required_secret_keys - set(secret_env)
    if missing_secrets:
        fail(f"required secret mapping {sorted(missing_secrets)[0]} is missing")
    normalized_secrets = {}
    for name, mapping in secret_env.items():
        if not isinstance(mapping, dict) or "secret_id" not in mapping or set(mapping) - {"secret_id", "version"}:
            fail(f"{name} secret mapping must contain exactly: secret_id, version")
        secret_id = required_string(mapping["secret_id"], f"{name} secret ID")
        version = mapping.get("version")
        if not isinstance(version, str) or not re.fullmatch(r"[1-9][0-9]*", version):
            fail(f"{name} secret version must be an exact positive integer")
        normalized_secrets[name] = {"secret_id": secret_id, "version": version}

    connector_selected = "INSTANCE_CONNECTION_NAME" in env_vars or "DB_NAME" in env_vars
    url_selected = "DATABASE_URL" in normalized_secrets
    if connector_selected == url_selected:
        fail("database contract must select exactly one of connector or URL mode")
    if connector_selected:
        connection_name = required_string(env_vars.get("INSTANCE_CONNECTION_NAME"), "INSTANCE_CONNECTION_NAME")
        database_name = env_vars.get("DB_NAME")
        if not isinstance(database_name, str) or not database_name.strip():
            fail("DB_NAME is required with INSTANCE_CONNECTION_NAME")
        parts = connection_name.split(":")
        if len(parts) != 3 or parts[0] != project_id or parts[1] != region or not parts[2]:
            fail("INSTANCE_CONNECTION_NAME must be project:region:instance and match the contract")
    else:
        required_string(normalized_secrets["DATABASE_URL"]["secret_id"], "DATABASE_URL secret ID")

    runtime_keys = {
        "allow_unauthenticated",
        "ingress",
        "port",
        "scaling",
        "resources",
        "timeout_seconds",
        "request_concurrency",
        "startup_probe",
        "liveness_probe",
    }
    runtime = exact_keys(source["runtime"], runtime_keys, "runtime")
    if runtime["allow_unauthenticated"] is not True:
        fail("first-party runtime must allow unauthenticated canonical traffic")
    if runtime["ingress"] != "INGRESS_TRAFFIC_ALL":
        fail("first-party runtime ingress must be INGRESS_TRAFFIC_ALL")
    if runtime["port"] != 8080:
        fail("runtime port must be 8080")
    scaling = exact_keys(runtime["scaling"], {"min_instances", "max_instances"}, "runtime.scaling")
    if any(isinstance(scaling[key], bool) or not isinstance(scaling[key], int) or scaling[key] < 0 for key in ("min_instances", "max_instances")):
        fail("runtime scaling must use non-negative integers")
    minimum = scaling["min_instances"]
    maximum = scaling["max_instances"]
    if maximum < max(minimum, 1):
        fail("runtime scaling must have max_instances greater than or equal to min_instances and at least 1")
    resources = exact_keys(
        runtime["resources"], {"cpu", "memory", "cpu_idle", "startup_cpu_boost"}, "runtime.resources"
    )
    if not isinstance(resources["cpu"], str) or not resources["cpu"] or not isinstance(resources["memory"], str) or not resources["memory"]:
        fail("runtime CPU and memory must be non-empty strings")
    if not re.fullmatch(r"(?:0\.[0-9]*[1-9][0-9]*|[1-9][0-9]*(?:\.[0-9]+)?)", resources["cpu"]):
        fail("runtime CPU must be a positive numeric value")
    memory_match = re.fullmatch(r"([1-9][0-9]*)(Mi|Gi)", resources["memory"])
    if memory_match is None:
        fail("runtime memory must be a positive Mi or Gi value")
    if not isinstance(resources["cpu_idle"], bool) or not isinstance(resources["startup_cpu_boost"], bool):
        fail("runtime CPU flags must be boolean")
    positive_integer(runtime["timeout_seconds"], "runtime timeout_seconds")
    positive_integer(runtime["request_concurrency"], "runtime request_concurrency")
    startup_probe = exact_keys(
        runtime["startup_probe"],
        {"path", "port", "timeout_seconds", "period_seconds", "failure_threshold"},
        "runtime.startup_probe",
    )
    liveness_probe = exact_keys(
        runtime["liveness_probe"],
        {"path", "port", "initial_delay_seconds", "timeout_seconds", "period_seconds", "failure_threshold"},
        "runtime.liveness_probe",
    )
    if startup_probe["path"] != "/health" or liveness_probe["path"] != "/health":
        fail("runtime probe paths must be /health")
    if startup_probe["port"] != 8080 or liveness_probe["port"] != 8080:
        fail("runtime probe ports must be 8080")
    for probe, keys in (
        (startup_probe, ("timeout_seconds", "period_seconds", "failure_threshold")),
        (liveness_probe, ("initial_delay_seconds", "timeout_seconds", "period_seconds", "failure_threshold")),
    ):
        for key in keys:
            positive_integer(probe[key], f"runtime probe {key}")

    rendered_env = dict(env_vars)
    rendered_env["WEBAUTHN_ALLOWED_ORIGINS"] = ",".join([canonical_origin, *android_origins])
    rendered_env["ANDROID_CERT_FINGERPRINTS"] = ",".join(fingerprints)
    rendered_env["RELEASE_VERSION"] = f"logdate-server@{release_sha}"
    contract = {
        "environment": environment,
        "release_sha": release_sha,
        "project_id": project_id,
        "region": region,
        "service_name": service_name,
        "canonical_origin": canonical_origin,
        "runtime_service_account": f"{runtime_service_account_name}@{project_id}.iam.gserviceaccount.com",
        "image": f"{region}-docker.pkg.dev/{project_id}/{artifact_registry_repo}/{artifact_registry_image_name}:{release_sha}",
        "env_vars": rendered_env,
        "secret_env": normalized_secrets,
        "runtime": runtime,
    }
    pathlib.Path(output_path).write_text(json.dumps(contract, separators=(",", ":")))
except ContractError as error:
    print(f"ERROR: {error}", file=sys.stderr)
    raise SystemExit(1)
except Exception:
    print("ERROR: deployment contract validation failed.", file=sys.stderr)
    raise SystemExit(1)
PY
then
    exit 1
fi

if ! jq -S . "$VALIDATED_JSON" >"$SORTED_JSON" 2>"$JQ_SORT_LOG"; then
    die "failed to sort the validated deployment contract."
fi

cat "$SORTED_JSON"
