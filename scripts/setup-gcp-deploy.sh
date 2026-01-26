#!/usr/bin/env bash
# setup-gcp-deploy.sh
# One-time setup for GCP infrastructure to enable keyless GitHub Actions deployment
#
# Prerequisites:
# - gcloud CLI installed and authenticated (project auto-detected from config)
# - GCP project with billing enabled
# - Domain logdate.app verified in Google Search Console
#
# Usage:
#   ./scripts/setup-gcp-deploy.sh                    # Interactive, auto-detects everything
#   SKIP_CONFIRM=true ./scripts/setup-gcp-deploy.sh  # Non-interactive
#
# Configuration priority (highest to lowest):
#   1. Environment variables passed at runtime
#   2. .env file in repo root (user overrides, gitignored)
#   3. docs/environment/.env.example (checked-in defaults)
#   4. Auto-detection (gcloud config, git remote)

set -euo pipefail

# Resolve script and repo root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source configuration files (lower priority first, so higher priority overwrites)
# .env.example provides checked-in defaults
if [[ -f "$REPO_ROOT/docs/environment/.env.example" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "$REPO_ROOT/docs/environment/.env.example"
    set +a
fi

# .env provides user overrides (gitignored)
if [[ -f "$REPO_ROOT/.env" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "$REPO_ROOT/.env"
    set +a
fi

# Runtime environment variables take final precedence (already in env)

# Apply auto-detection for values still not set
GCP_PROJECT_ID="${GCP_PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"

# Map sourced variables to script variables
PROJECT_ID="$GCP_PROJECT_ID"
REGION="${GCP_REGION:-us-central1}"
SERVICE_NAME="${CLOUD_RUN_SERVICE_NAME:-logdate-server}"
DOMAIN="${CLOUD_RUN_DOMAIN:-cloud.logdate.app}"
ARTIFACT_REGISTRY_REPO="${ARTIFACT_REGISTRY_REPO:-logdate}"

# GitHub repo (may be sourced or auto-detected below)
GITHUB_REPO="${GITHUB_REPO:-}"

# Fixed infrastructure names (not typically changed)
POOL_NAME="logdate-github-pool"
PROVIDER_NAME="github-provider"
SERVICE_ACCOUNT_NAME="github-deploy"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Resolve GitHub repo if not provided (auto-detect from git remote)
if [[ -z "$GITHUB_REPO" ]]; then
    if command -v git &> /dev/null; then
        REPO_URL=$(git -C "$REPO_ROOT" config --get remote.origin.url 2>/dev/null || true)
        if [[ "$REPO_URL" =~ github\.com[:/](.+?)(\.git)?$ ]]; then
            GITHUB_REPO="${BASH_REMATCH[1]}"
        fi
    fi
fi

if [[ -z "$GITHUB_REPO" ]]; then
    log_error "GITHUB_REPO is required (format: owner/repo)"
    echo "Usage: GCP_PROJECT_ID=your-project-id GITHUB_REPO=owner/repo ./scripts/setup-gcp-deploy.sh"
    exit 1
fi

# Validate prerequisites
if [[ -z "$PROJECT_ID" ]]; then
    log_error "GCP_PROJECT_ID environment variable is required"
    echo "Usage: GCP_PROJECT_ID=your-project-id ./scripts/setup-gcp-deploy.sh"
    exit 1
fi

if ! command -v gcloud &> /dev/null; then
    log_error "gcloud CLI is not installed"
    exit 1
fi

# Check if authenticated
if ! gcloud auth print-identity-token &> /dev/null; then
    log_error "Not authenticated with gcloud. Run: gcloud auth login"
    exit 1
fi

# Show configuration and confirm
echo ""
echo "=============================================="
echo "GCP Deployment Setup - Configuration"
echo "=============================================="
echo ""
echo "Sources (in priority order):"
echo "  1. Runtime env vars"
[[ -f "$REPO_ROOT/.env" ]] && echo "  2. $REPO_ROOT/.env (found)"
echo "  3. docs/environment/.env.example (defaults)"
echo "  4. Auto-detection (gcloud, git)"
echo ""
echo "Resolved values:"
echo "  GCP_PROJECT_ID:        $PROJECT_ID"
echo "  GCP_REGION:            $REGION"
echo "  CLOUD_RUN_SERVICE_NAME: $SERVICE_NAME"
echo "  CLOUD_RUN_DOMAIN:      $DOMAIN"
echo "  GITHUB_REPO:           $GITHUB_REPO"
echo "  ARTIFACT_REGISTRY_REPO: $ARTIFACT_REGISTRY_REPO"
echo "=============================================="
echo ""

if [[ "${SKIP_CONFIRM:-}" != "true" ]]; then
    read -p "Proceed with setup? [y/N] " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        log_info "Aborted."
        exit 0
    fi
fi

log_info "Setting up GCP deployment infrastructure..."

# Set the project
gcloud config set project "$PROJECT_ID"

# Enable required APIs
log_info "Enabling required GCP APIs..."
gcloud services enable \
    iam.googleapis.com \
    iamcredentials.googleapis.com \
    cloudresourcemanager.googleapis.com \
    run.googleapis.com \
    artifactregistry.googleapis.com \
    --quiet

# Create Artifact Registry repository if it doesn't exist
log_info "Creating Artifact Registry repository..."
if ! gcloud artifacts repositories describe "$ARTIFACT_REGISTRY_REPO" \
    --location="$REGION" &> /dev/null; then
    gcloud artifacts repositories create "$ARTIFACT_REGISTRY_REPO" \
        --repository-format=docker \
        --location="$REGION" \
        --description="LogDate container images"
    log_info "Created Artifact Registry repository: $ARTIFACT_REGISTRY_REPO"
else
    log_info "Artifact Registry repository already exists"
fi

# Create Workload Identity Pool
log_info "Creating Workload Identity Pool..."
if ! gcloud iam workload-identity-pools describe "$POOL_NAME" \
    --location="global" &> /dev/null; then
    gcloud iam workload-identity-pools create "$POOL_NAME" \
        --location="global" \
        --display-name="LogDate GitHub Actions Pool" \
        --description="Pool for GitHub Actions OIDC authentication"
    log_info "Created Workload Identity Pool: $POOL_NAME"
else
    log_info "Workload Identity Pool already exists"
fi

# Create Workload Identity Provider
log_info "Creating Workload Identity Provider..."
if ! gcloud iam workload-identity-pools providers describe "$PROVIDER_NAME" \
    --location="global" \
    --workload-identity-pool="$POOL_NAME" &> /dev/null; then
    gcloud iam workload-identity-pools providers create-oidc "$PROVIDER_NAME" \
        --location="global" \
        --workload-identity-pool="$POOL_NAME" \
        --issuer-uri="https://token.actions.githubusercontent.com" \
        --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" \
        --attribute-condition="assertion.repository == '${GITHUB_REPO}'"
    log_info "Created Workload Identity Provider: $PROVIDER_NAME"
else
    log_info "Workload Identity Provider already exists"
fi

# Create service account
log_info "Creating service account..."
SA_EMAIL="${SERVICE_ACCOUNT_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
if ! gcloud iam service-accounts describe "$SA_EMAIL" &> /dev/null; then
    gcloud iam service-accounts create "$SERVICE_ACCOUNT_NAME" \
        --display-name="GitHub Actions Deploy Account" \
        --description="Service account for GitHub Actions to deploy to Cloud Run"
    log_info "Created service account: $SA_EMAIL"
else
    log_info "Service account already exists"
fi

# Grant roles to service account
log_info "Granting roles to service account..."
ROLES=(
    "roles/run.admin"
    "roles/artifactregistry.writer"
    "roles/iam.serviceAccountUser"
    "roles/run.invoker"
)

for role in "${ROLES[@]}"; do
    gcloud projects add-iam-policy-binding "$PROJECT_ID" \
        --member="serviceAccount:$SA_EMAIL" \
        --role="$role" \
        --quiet
    log_info "Granted $role"
done

# Bind Workload Identity to service account
log_info "Binding Workload Identity to service account..."
WORKLOAD_IDENTITY_POOL_ID=$(gcloud iam workload-identity-pools describe "$POOL_NAME" \
    --location="global" \
    --format="value(name)")

gcloud iam service-accounts add-iam-policy-binding "$SA_EMAIL" \
    --role="roles/iam.workloadIdentityUser" \
    --member="principalSet://iam.googleapis.com/${WORKLOAD_IDENTITY_POOL_ID}/attribute.repository/${GITHUB_REPO}" \
    --quiet

log_info "Workload Identity binding complete"

# Get the Workload Identity Provider resource name
PROVIDER_RESOURCE_NAME=$(gcloud iam workload-identity-pools providers describe "$PROVIDER_NAME" \
    --location="global" \
    --workload-identity-pool="$POOL_NAME" \
    --format="value(name)")

# Create domain mapping (may fail if domain not verified)
log_info "Setting up domain mapping for $DOMAIN..."
if gcloud beta run domain-mappings describe \
    --domain="$DOMAIN" \
    --region="$REGION" &> /dev/null; then
    log_info "Domain mapping already exists"
else
    log_warn "Domain mapping will be created when the service is first deployed."
    log_warn "Make sure the domain is verified in Google Search Console first."
    log_info "After first deployment, run:"
    echo "  gcloud beta run domain-mappings create --service $SERVICE_NAME --domain $DOMAIN --region $REGION"
fi

# Output summary
echo ""
echo "=============================================="
echo -e "${GREEN}Setup Complete!${NC}"
echo "=============================================="
echo ""
echo "Add these secrets to your GitHub repository"
echo "(Settings → Secrets and variables → Actions):"
echo ""
echo -e "${YELLOW}GCP_PROJECT_ID${NC}="
echo "  $PROJECT_ID"
echo ""
echo -e "${YELLOW}GCP_WORKLOAD_IDENTITY_PROVIDER${NC}="
echo "  $PROVIDER_RESOURCE_NAME"
echo ""
echo -e "${YELLOW}GCP_SERVICE_ACCOUNT${NC}="
echo "  $SA_EMAIL"
echo ""
echo "=============================================="
echo "Also add these application secrets:"
echo "  - DATABASE_URL"
echo "  - DATABASE_USER"
echo "  - DATABASE_PASSWORD"
echo "  - JWT_SECRET"
echo "  - GCS_BUCKET_NAME"
echo "  - REDIS_URL"
echo "=============================================="
echo ""
echo "DNS Configuration:"
echo "Add this CNAME record at your DNS provider:"
echo ""
echo "  Type:  CNAME"
echo "  Name:  cloud"
echo "  Value: ghs.googlehosted.com."
echo ""
echo "=============================================="
