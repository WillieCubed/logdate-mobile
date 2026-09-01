#!/usr/bin/env bash
#
# Provisions the Sentry DSN for one environment.
#
# The runbook spelled this out as three gcloud invocations with a project, a
# service, a region and a runtime service account to keep straight between
# them. That is four values to transcribe correctly at the exact moment
# somebody is pasting a secret out of a dashboard, which is how the secret ends
# up in the wrong project or the binding gets skipped and the deploy fails on a
# permission error half an hour later.
#
# Usage:
#   scripts/set-sentry-dsn.sh production   # reads the DSN from stdin
#   scripts/set-sentry-dsn.sh staging
#
# The DSN is read from stdin rather than taken as an argument so it never lands
# in shell history or a process listing.
set -euo pipefail

readonly ENVIRONMENT="${1:?environment required: production | staging}"
readonly SECRET="logdate-sentry-dsn"
readonly REGION="us-central1"

case "$ENVIRONMENT" in
    production) GCP_PROJECT="logdate"; SERVICE="logdate-server" ;;
    staging) GCP_PROJECT="logdate-dev"; SERVICE="logdate-server-staging" ;;
    *) echo "unknown environment: $ENVIRONMENT (expected production or staging)" >&2; exit 1 ;;
esac

printf 'Paste the Sentry DSN for %s and press enter (input hidden): ' "$ENVIRONMENT" >&2
read -r -s DSN
printf '\n' >&2

[[ -n "$DSN" ]] || { echo "no DSN provided" >&2; exit 1; }
# A DSN that is not a DSN fails at boot rather than here, where the mistake is
# still cheap to correct.
[[ "$DSN" =~ ^https://[^@]+@[^/]+/[0-9]+$ ]] || {
    echo "that does not look like a Sentry DSN (expected https://<key>@<host>/<project-id>)" >&2
    exit 1
}

if gcloud secrets describe "$SECRET" --project="$GCP_PROJECT" >/dev/null 2>&1; then
    printf '%s' "$DSN" | gcloud secrets versions add "$SECRET" --project="$GCP_PROJECT" --data-file=-
else
    printf '%s' "$DSN" |
        gcloud secrets create "$SECRET" \
            --project="$GCP_PROJECT" \
            --replication-policy=automatic \
            --data-file=-
fi

RUNTIME_SA="$(
    gcloud run services describe "$SERVICE" \
        --project="$GCP_PROJECT" --region="$REGION" \
        --format='value(spec.template.spec.serviceAccountName)'
)"
[[ -n "$RUNTIME_SA" ]] || { echo "could not resolve the runtime service account for $SERVICE" >&2; exit 1; }

gcloud secrets add-iam-policy-binding "$SECRET" \
    --project="$GCP_PROJECT" \
    --member="serviceAccount:$RUNTIME_SA" \
    --role=roles/secretmanager.secretAccessor \
    --condition=None >/dev/null

gcloud run services update "$SERVICE" \
    --project="$GCP_PROJECT" --region="$REGION" \
    --update-secrets="SENTRY_DSN=$SECRET:latest" >/dev/null

echo "SENTRY_DSN is mounted on $SERVICE ($GCP_PROJECT)."
echo "Confirm at boot with:"
echo "  gcloud logging read 'resource.labels.service_name=\"$SERVICE\" AND textPayload:\"Sentry initialised\"' --project=$GCP_PROJECT --freshness=5m --limit=5"
