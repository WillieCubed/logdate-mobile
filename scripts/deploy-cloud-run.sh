#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${GCP_PROJECT_ID:-${PROJECT_ID:-}}"
REGION="${CLOUD_RUN_REGION:-${REGION:-us-central1}}"
SERVICE_NAME="${CLOUD_RUN_SERVICE_NAME:-${SERVICE_NAME:-logdate-server}}"
DOMAIN="${CLOUD_RUN_DOMAIN:-${DOMAIN:-}}"
ARTIFACT_REGISTRY_REPO="${ARTIFACT_REGISTRY_REPO:-logdate}"
DOCKER_PLATFORM="${DOCKER_PLATFORM:-linux/amd64}"

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

REQUIRED_VARS=(
    DATABASE_URL
    DATABASE_USER
    DATABASE_PASSWORD
    JWT_SECRET
    GCS_BUCKET_NAME
)

for var in "${REQUIRED_VARS[@]}"; do
    if [[ -z "${!var:-}" ]]; then
        echo "$var is required in the environment for manual deploys."
        exit 1
    fi
done

GCS_PROJECT_ID_VALUE="${GCS_PROJECT_ID:-$PROJECT_ID}"
WEBAUTHN_RP_ID_VALUE="${WEBAUTHN_RP_ID:-$DOMAIN}"
WEBAUTHN_ORIGIN_VALUE="${WEBAUTHN_ORIGIN:-}"
if [[ -z "$WEBAUTHN_ORIGIN_VALUE" && -n "$DOMAIN" ]]; then
    WEBAUTHN_ORIGIN_VALUE="https://$DOMAIN"
fi

RUNTIME_SERVICE_ACCOUNT="${CLOUD_RUN_SERVICE_ACCOUNT:-${RUNTIME_SERVICE_ACCOUNT:-}}"
SERVICE_ACCOUNT_FLAG=""
if [[ -n "$RUNTIME_SERVICE_ACCOUNT" ]]; then
    SERVICE_ACCOUNT_FLAG="--service-account $RUNTIME_SERVICE_ACCOUNT"
fi

IMAGE_URI="$REGION-docker.pkg.dev/$PROJECT_ID/$ARTIFACT_REGISTRY_REPO/$SERVICE_NAME"

EXTRA_ENV_FLAGS=()
if [[ -n "$WEBAUTHN_RP_ID_VALUE" ]]; then
    EXTRA_ENV_FLAGS+=(--set-env-vars "WEBAUTHN_RP_ID=$WEBAUTHN_RP_ID_VALUE")
fi
if [[ -n "$WEBAUTHN_ORIGIN_VALUE" ]]; then
    EXTRA_ENV_FLAGS+=(--set-env-vars "WEBAUTHN_ORIGIN=$WEBAUTHN_ORIGIN_VALUE")
fi
if [[ -n "${REDIS_URL:-}" ]]; then
    EXTRA_ENV_FLAGS+=(--set-env-vars "REDIS_URL=$REDIS_URL")
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
    $SERVICE_ACCOUNT_FLAG \
    --set-env-vars "DATABASE_URL=$DATABASE_URL" \
    --set-env-vars "DATABASE_USER=$DATABASE_USER" \
    --set-env-vars "DATABASE_PASSWORD=$DATABASE_PASSWORD" \
    --set-env-vars "JWT_SECRET=$JWT_SECRET" \
    --set-env-vars "GCS_BUCKET_NAME=$GCS_BUCKET_NAME" \
    --set-env-vars "GCS_PROJECT_ID=$GCS_PROJECT_ID_VALUE" \
    "${EXTRA_ENV_FLAGS[@]}" \
    --allow-unauthenticated \
    --min-instances "${MIN_INSTANCES:-0}" \
    --max-instances "${MAX_INSTANCES:-10}" \
    --memory "${MEMORY:-512Mi}" \
    --cpu "${CPU:-1}" \
    --timeout "${TIMEOUT:-60s}"

SERVICE_URL=$(gcloud run services describe "$SERVICE_NAME" \
    --platform managed \
    --region "$REGION" \
    --format 'value(status.url)')

echo "Cloud Run URL: $SERVICE_URL"
