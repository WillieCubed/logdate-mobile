#!/usr/bin/env bash
#
# Pin, and then continuously re-check, which Android certificate is
# authoritative for an environment.
#
# The certificate an APK is signed with and the origin the server allowlists
# have to be the same key. Nothing enforced that: the fingerprint travelled by
# hand from a keystore into Terraform, into Digital Asset Links, and into the
# rendered contract, and a mismatch only surfaced as a failed passkey ceremony
# on a user's device, long after the build.
#
# This records the authoritative certificates per environment as a committed
# file, and validates a rendered contract against it. A silent certificate swap
# fails the build instead of the ceremony.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

KNOWN_DEBUG_FINGERPRINT="DF:32:69:D4:DC:C9:C4:FE:72:FE:61:62:A0:F4:E9:EE:5F:04:14:47:DC:B3:8E:F6:A9:25:76:FC:38:90:DB:C7"

COMMAND="${1:-}"
[[ $# -gt 0 ]] && shift || true

ENVIRONMENT=""
EVIDENCE_FILE=""
RENDERED_CONTRACT=""
SOURCE_SHA=""

die() {
    printf 'ERROR: %s\n' "$1" >&2
    exit 1
}

print_usage() {
    cat <<'EOF'
android-signer-evidence.sh

Pin the Android signing certificates that are authoritative for an environment,
and verify a rendered Cloud Run contract still agrees with them.

Usage:
  ./scripts/android-signer-evidence.sh generate --environment ENV [--evidence-file PATH]
  ./scripts/android-signer-evidence.sh validate --environment ENV \
      --evidence-file PATH --rendered-contract PATH --source-sha SHA

Commands:
  generate   Render the contract for ENV and write its signing set to the
             committed evidence file. Run this when a certificate legitimately
             changes -- adding the Play app-signing certificate, or rotating a
             signer -- so the change is reviewable in a diff.
  validate   Fail if a rendered contract disagrees with the committed evidence.

Options:
  --environment ENV         staging | production
  --evidence-file PATH      Defaults to
                            infra/android-signing/ENV-signer-evidence.json
  --rendered-contract PATH  Contract to check (validate only)
  --source-sha SHA          Commit the contract was rendered at (validate only)
  --help, -h                Show this help
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --environment) ENVIRONMENT="${2:-}"; shift 2 ;;
        --evidence-file) EVIDENCE_FILE="${2:-}"; shift 2 ;;
        --rendered-contract) RENDERED_CONTRACT="${2:-}"; shift 2 ;;
        --source-sha) SOURCE_SHA="${2:-}"; shift 2 ;;
        --help|-h) print_usage; exit 0 ;;
        *) print_usage; die "Unknown argument: $1" ;;
    esac
done

case "$COMMAND" in
    generate|validate) ;;
    ""|--help|-h) print_usage; exit 0 ;;
    *) print_usage; die "Unknown command: $COMMAND" ;;
esac

case "$ENVIRONMENT" in
    staging|production) ;;
    "") die "--environment is required." ;;
    *) die "--environment must be 'staging' or 'production' (got '$ENVIRONMENT')." ;;
esac

command -v jq >/dev/null || die "jq is required."
command -v python3 >/dev/null || die "python3 is required."

EVIDENCE_FILE="${EVIDENCE_FILE:-$REPO_ROOT/infra/android-signing/${ENVIRONMENT}-signer-evidence.json}"

if [[ "$COMMAND" == "generate" ]]; then
    SOURCE_SHA="${SOURCE_SHA:-$(git -C "$REPO_ROOT" rev-parse HEAD)}"
    WORKDIR="$(mktemp -d)"
    trap 'rm -rf "$WORKDIR"' EXIT
    bash "$REPO_ROOT/scripts/render-cloud-run-contract.sh" \
        --environment "$ENVIRONMENT" \
        --release-sha "$SOURCE_SHA" >"$WORKDIR/contract.json" ||
        die "could not render the $ENVIRONMENT contract to generate evidence from."
    mkdir -p "$(dirname "$EVIDENCE_FILE")"
    jq -S \
        --arg environment "$ENVIRONMENT" \
        '{
            environment: $environment,
            package_name: .android_package_name,
            expected_build_signer_role: .android_signing.expected_build_signer_role,
            expected_build_signer_fingerprint: .android_signing.expected_build_signer_fingerprint,
            expected_build_signer_origin: .android_signing.expected_build_signer_origin,
            certificates: .android_signing.certificates
        }' "$WORKDIR/contract.json" >"$EVIDENCE_FILE"
    printf 'Wrote %s\n' "$EVIDENCE_FILE"
    exit 0
fi

[[ -n "$RENDERED_CONTRACT" ]] || die "--rendered-contract is required for validate."
[[ -n "$SOURCE_SHA" ]] || die "--source-sha is required for validate."
[[ -f "$EVIDENCE_FILE" ]] || die "evidence file not found: $EVIDENCE_FILE"
[[ -f "$RENDERED_CONTRACT" ]] || die "rendered contract not found: $RENDERED_CONTRACT"
[[ "$SOURCE_SHA" =~ ^[0-9a-f]{40}$ ]] || die "--source-sha must be 40 lowercase hexadecimal characters."

python3 - "$EVIDENCE_FILE" "$RENDERED_CONTRACT" "$ENVIRONMENT" "$KNOWN_DEBUG_FINGERPRINT" "$SOURCE_SHA" <<'PY'
import base64
import json
import pathlib
import sys

evidence_path, contract_path, environment, known_debug, source_sha = sys.argv[1:6]
evidence = json.loads(pathlib.Path(evidence_path).read_text())
contract = json.loads(pathlib.Path(contract_path).read_text())

failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


# Without this the evidence is not commit-bound at all: a contract rendered
# before a certificate was rotated would validate against the new commit.
check(
    contract.get("release_sha") == source_sha,
    f"contract was rendered at {contract.get('release_sha')!r}, not at {source_sha!r}",
)
check(
    evidence.get("environment") == environment,
    f"evidence environment {evidence.get('environment')!r} does not match {environment!r}",
)
check(
    evidence.get("package_name") == contract.get("android_package_name"),
    "evidence package_name does not match the contract's android_package_name",
)

signing = contract.get("android_signing") or {}
check(
    evidence.get("certificates") == signing.get("certificates"),
    "the contract's signing certificates differ from the pinned evidence",
)
for field in (
    "expected_build_signer_role",
    "expected_build_signer_fingerprint",
    "expected_build_signer_origin",
):
    check(
        evidence.get(field) == signing.get(field),
        f"the contract's {field} differs from the pinned evidence",
    )

allowed = (contract.get("env_vars") or {}).get("WEBAUTHN_ALLOWED_ORIGINS", "")
allowed_origins = {value for value in allowed.split(",") if value}

for role, certificate in (evidence.get("certificates") or {}).items():
    fingerprint = certificate.get("fingerprint", "")
    origin = certificate.get("apk_key_hash_origin", "")

    check(
        fingerprint.upper() != known_debug.upper(),
        f"{role} pins the known debug certificate",
    )

    # Recompute the origin rather than trusting the pair. These are two
    # encodings of one digest; if they disagree the ceremony fails on device.
    try:
        digest = bytes.fromhex(fingerprint.replace(":", ""))
    except ValueError:
        failures.append(f"{role} fingerprint is not colon-hex SHA-256")
        continue
    if len(digest) != 32:
        failures.append(f"{role} fingerprint is not 32 bytes")
        continue
    expected = "android:apk-key-hash:" + base64.urlsafe_b64encode(digest).decode().rstrip("=")
    check(
        origin == expected,
        f"{role} apk-key-hash origin does not match its own fingerprint",
    )
    check(
        origin in allowed_origins,
        f"{role} origin is absent from WEBAUTHN_ALLOWED_ORIGINS, so an install "
        f"signed with it could not complete a passkey ceremony",
    )

if failures:
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)
    raise SystemExit(1)

roles = ",".join(sorted((evidence.get("certificates") or {}).keys()))
print(f"{environment} signer evidence matches the rendered contract ({roles}).")
PY
