#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ENVIRONMENT="${ENVIRONMENT:-staging}"
CONFIG_PATH="${CONFIG_PATH:-${REPO_ROOT}/infra/terraform/${ENVIRONMENT}.tfvars}"
CONFIG_MODE="${CONFIG_MODE:-image}"

PROJECT_ID="${GCP_PROJECT_ID:-${PROJECT_ID:-}}"
REGION="${CLOUD_RUN_REGION:-${REGION:-}}"
SERVICE_NAME="${CLOUD_RUN_SERVICE_NAME:-${SERVICE_NAME:-}}"
DOMAIN="${CLOUD_RUN_DOMAIN:-${DOMAIN:-}}"
ARTIFACT_REGISTRY_REPO="${ARTIFACT_REGISTRY_REPO:-}"
RUNTIME_SERVICE_ACCOUNT="${CLOUD_RUN_SERVICE_ACCOUNT:-${RUNTIME_SERVICE_ACCOUNT:-}}"
DOCKER_PLATFORM="${DOCKER_PLATFORM:-linux/amd64}"

if [[ "$CONFIG_MODE" != "image" && "$CONFIG_MODE" != "full" ]]; then
    echo "CONFIG_MODE must be 'image' or 'full'."
    exit 1
fi

TF_DIR="${REPO_ROOT}/infra/terraform"

tf_eval_raw() {
    terraform -chdir="$TF_DIR" console -var-file="$CONFIG_PATH" <<EOF | sed -e 's/^> //'
$1
EOF
}

tf_value() {
    local value
    value="$(tf_eval_raw "var.$1" | sed '/^$/d' | tail -n 1)"
    value="${value%\"}"
    value="${value#\"}"
    if [[ "$value" == "null" ]]; then
        value=""
    fi
    echo "$value"
}

tf_lines() {
    tf_eval_raw "$1" | sed -e '/^<<EOT$/d' -e '/^EOT$/d' -e 's/^"//' -e 's/"$//'
}

if [[ -f "$CONFIG_PATH" ]]; then
    if ! command -v terraform &> /dev/null; then
        echo "terraform is required to read $CONFIG_PATH."
        exit 1
    fi

    terraform -chdir="$TF_DIR" init -backend=false -input=false >/dev/null

    PROJECT_ID="${PROJECT_ID:-$(tf_value project_id)}"
    REGION="${REGION:-$(tf_value region)}"
    SERVICE_NAME="${SERVICE_NAME:-$(tf_value service_name)}"
    DOMAIN="${DOMAIN:-$(tf_value domain)}"
    ARTIFACT_REGISTRY_REPO="${ARTIFACT_REGISTRY_REPO:-$(tf_value artifact_registry_repo)}"

    RUNTIME_SERVICE_ACCOUNT_NAME="$(tf_value runtime_service_account_name)"
    if [[ -z "$RUNTIME_SERVICE_ACCOUNT" && -n "$PROJECT_ID" && -n "$RUNTIME_SERVICE_ACCOUNT_NAME" ]]; then
        RUNTIME_SERVICE_ACCOUNT="${RUNTIME_SERVICE_ACCOUNT_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
    fi
else
    echo "Config file not found: $CONFIG_PATH (set CONFIG_PATH or ENVIRONMENT to use shared config)."
fi

REGION="${REGION:-us-central1}"
SERVICE_NAME="${SERVICE_NAME:-logdate-server}"
ARTIFACT_REGISTRY_REPO="${ARTIFACT_REGISTRY_REPO:-logdate}"

IMAGE_TAG="${IMAGE_TAG:-}"
if [[ -z "$IMAGE_TAG" ]]; then
    if command -v git &> /dev/null && git rev-parse --is-inside-work-tree &> /dev/null; then
        IMAGE_TAG="$(git rev-parse --short HEAD)"
    else
        IMAGE_TAG="latest"
    fi
fi

if [[ -z "$PROJECT_ID" ]]; then
    echo "GCP_PROJECT_ID (or PROJECT_ID) is required."
    exit 1
fi

if ! command -v gcloud &> /dev/null; then
    echo "gcloud CLI is required."
    exit 1
fi

if ! command -v docker &> /dev/null; then
    echo "docker is required."
    exit 1
fi

if [[ "$CONFIG_MODE" == "full" && ! -f "$CONFIG_PATH" ]]; then
    echo "CONFIG_MODE=full requires a config file at $CONFIG_PATH."
    exit 1
fi

if [[ "$CONFIG_MODE" == "image" ]]; then
    if ! gcloud run services describe "$SERVICE_NAME" \
        --platform managed \
        --region "$REGION" \
        --project "$PROJECT_ID" &> /dev/null; then
        echo "Cloud Run service not found. Use CONFIG_MODE=full to bootstrap config."
        exit 1
    fi
fi

SERVICE_ACCOUNT_ARGS=()
if [[ -n "$RUNTIME_SERVICE_ACCOUNT" ]]; then
    SERVICE_ACCOUNT_ARGS+=(--service-account "$RUNTIME_SERVICE_ACCOUNT")
fi

IMAGE_URI="$REGION-docker.pkg.dev/$PROJECT_ID/$ARTIFACT_REGISTRY_REPO/$SERVICE_NAME"

CONFIG_ENV_ARGS=()
CONFIG_SECRET_ARGS=()
CONFIG_RUNTIME_ARGS=()

if [[ "$CONFIG_MODE" == "full" ]]; then
    GCS_BUCKET_NAME_VALUE="${GCS_BUCKET_NAME:-$(tf_value gcs_bucket_name)}"
    WEBAUTHN_RP_ID_VALUE="${WEBAUTHN_RP_ID:-$(tf_value webauthn_rp_id)}"
    WEBAUTHN_ORIGIN_VALUE="${WEBAUTHN_ORIGIN:-$(tf_value webauthn_origin)}"
    GCS_PROJECT_ID_VALUE="${GCS_PROJECT_ID:-$PROJECT_ID}"

    if [[ -z "$WEBAUTHN_ORIGIN_VALUE" && -n "$WEBAUTHN_RP_ID_VALUE" ]]; then
        WEBAUTHN_ORIGIN_VALUE="https://$WEBAUTHN_RP_ID_VALUE"
    fi

    CONFIG_ENV_ARGS+=(--set-env-vars "PORT=8080")
    CONFIG_ENV_ARGS+=(--set-env-vars "HOST=0.0.0.0")
    CONFIG_ENV_ARGS+=(--set-env-vars "GCS_PROJECT_ID=$GCS_PROJECT_ID_VALUE")

    if [[ -n "$GCS_BUCKET_NAME_VALUE" ]]; then
        CONFIG_ENV_ARGS+=(--set-env-vars "GCS_BUCKET_NAME=$GCS_BUCKET_NAME_VALUE")
    fi
    if [[ -n "$WEBAUTHN_RP_ID_VALUE" ]]; then
        CONFIG_ENV_ARGS+=(--set-env-vars "WEBAUTHN_RP_ID=$WEBAUTHN_RP_ID_VALUE")
    fi
    if [[ -n "$WEBAUTHN_ORIGIN_VALUE" ]]; then
        CONFIG_ENV_ARGS+=(--set-env-vars "WEBAUTHN_ORIGIN=$WEBAUTHN_ORIGIN_VALUE")
    fi

    while IFS= read -r env_pair; do
        [[ -z "$env_pair" ]] && continue
        CONFIG_ENV_ARGS+=(--set-env-vars "$env_pair")
    done < <(tf_lines 'join("\n", [for k, v in var.cloud_run_env : "${k}=${v}"])')

    while IFS= read -r secret_pair; do
        [[ -z "$secret_pair" ]] && continue
        CONFIG_SECRET_ARGS+=(--set-secrets "$secret_pair")
    done < <(tf_lines 'join("\n", [for k, v in var.cloud_run_secret_env : "${k}=${v.secret_id}:${try(v.version, "latest")}"])')

    ALLOW_UNAUTHENTICATED_VALUE="${ALLOW_UNAUTHENTICATED:-$(tf_value allow_unauthenticated)}"
    if [[ "$ALLOW_UNAUTHENTICATED_VALUE" == "true" ]]; then
        CONFIG_RUNTIME_ARGS+=(--allow-unauthenticated)
    fi

    MIN_INSTANCES_VALUE="${MIN_INSTANCES:-$(tf_value min_instances)}"
    MAX_INSTANCES_VALUE="${MAX_INSTANCES:-$(tf_value max_instances)}"
    MEMORY_VALUE="${MEMORY:-$(tf_value memory)}"
    CPU_VALUE="${CPU:-$(tf_value cpu)}"
    TIMEOUT_SECONDS_VALUE="${TIMEOUT_SECONDS:-$(tf_value timeout_seconds)}"

    MIN_INSTANCES_VALUE="${MIN_INSTANCES_VALUE:-0}"
    MAX_INSTANCES_VALUE="${MAX_INSTANCES_VALUE:-10}"
    MEMORY_VALUE="${MEMORY_VALUE:-512Mi}"
    CPU_VALUE="${CPU_VALUE:-1}"
    TIMEOUT_SECONDS_VALUE="${TIMEOUT_SECONDS_VALUE:-60}"

    if [[ -n "${TIMEOUT:-}" ]]; then
        CONFIG_RUNTIME_ARGS+=(--timeout "${TIMEOUT}")
    else
        CONFIG_RUNTIME_ARGS+=(--timeout "${TIMEOUT_SECONDS_VALUE}s")
    fi

    CONFIG_RUNTIME_ARGS+=(--min-instances "$MIN_INSTANCES_VALUE")
    CONFIG_RUNTIME_ARGS+=(--max-instances "$MAX_INSTANCES_VALUE")
    CONFIG_RUNTIME_ARGS+=(--memory "$MEMORY_VALUE")
    CONFIG_RUNTIME_ARGS+=(--cpu "$CPU_VALUE")
fi

gcloud config set project "$PROJECT_ID"

gcloud auth configure-docker "$REGION-docker.pkg.dev"

docker buildx build \
    --platform "$DOCKER_PLATFORM" \
    --target production \
    -t "$IMAGE_URI:$IMAGE_TAG" \
    -t "$IMAGE_URI:latest" \
    --load \
    .

docker push "$IMAGE_URI:$IMAGE_TAG"
if [[ "$IMAGE_TAG" != "latest" ]]; then
    docker push "$IMAGE_URI:latest"
fi

gcloud run deploy "$SERVICE_NAME" \
    --image "$IMAGE_URI:$IMAGE_TAG" \
    --platform managed \
    --region "$REGION" \
    --project "$PROJECT_ID" \
    "${SERVICE_ACCOUNT_ARGS[@]}" \
    "${CONFIG_ENV_ARGS[@]}" \
    "${CONFIG_SECRET_ARGS[@]}" \
    "${CONFIG_RUNTIME_ARGS[@]}"

SERVICE_URL=$(gcloud run services describe "$SERVICE_NAME" \
    --platform managed \
    --region "$REGION" \
    --project "$PROJECT_ID" \
    --format 'value(status.url)')

echo "Cloud Run URL: $SERVICE_URL"
