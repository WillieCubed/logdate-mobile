# Sentry error reporting

The LogDate server forwards uncaught exceptions and ERROR-level log events to
[Sentry](https://sentry.io) via the standard JVM SDK plus the logback
appender. With no DSN configured the SDK is a no-op, so dev/test boots
cleanly without any network traffic; once `SENTRY_DSN` is mounted in Cloud
Run the integration becomes live.

## What gets captured

- **Uncaught exceptions** in any thread — Sentry installs a JVM
  `UncaughtExceptionHandler` during `Sentry.init`.
- **ERROR-level log events** (Napier.e, SLF4J `logger.error`, anything
  routed through logback at ERROR) — captured as Sentry events by the
  `io.sentry.logback.SentryAppender` configured in
  `server/src/main/resources/logback.xml`.
- **WARN-level log events** — attached as breadcrumbs to subsequent
  events, useful for reconstructing what happened just before an error.
- Each event carries the configured `environment` tag (`production` or
  `development`) and a `release` of `logdate-server@1.0.0`.

What's *not* captured today: route-level handled exceptions that get
swallowed by `try { … } catch (e: Exception) { call.respondApiError(…) }`
patterns *without* a Napier.e or SLF4J error log. Those return an HTTP
error to the client but never surface in Sentry. The auth route handlers
already log their failures; new routes should follow the same pattern.

## Provisioning the DSN

Create the Sentry project in the dashboard
(<https://sentry.io>/settings/projects/) and copy the DSN. It looks like
`https://<key>@<org>.ingest.sentry.io/<project-id>` — opaque, includes the
secret, must not land in source control.

For each environment (staging = `logdate-dev`, production = `logdate`):

```bash
PROJECT=logdate-dev          # or logdate
SERVICE=logdate-server-staging   # or logdate-server
REGION=us-central1
DSN='https://...'                # paste from Sentry dashboard

# 1. Create the Secret Manager secret.
echo -n "$DSN" \
  | gcloud secrets create logdate-sentry-dsn \
      --project="$PROJECT" \
      --replication-policy=automatic \
      --data-file=-

# 2. Grant the Cloud Run runtime SA read access.
RUNTIME_SA=$(gcloud run services describe "$SERVICE" \
  --project="$PROJECT" --region="$REGION" \
  --format='value(spec.template.spec.serviceAccountName)')

gcloud secrets add-iam-policy-binding logdate-sentry-dsn \
  --project="$PROJECT" \
  --member="serviceAccount:$RUNTIME_SA" \
  --role=roles/secretmanager.secretAccessor

# 3. Mount the secret as the SENTRY_DSN env var.
gcloud run services update "$SERVICE" \
  --project="$PROJECT" --region="$REGION" \
  --update-secrets=SENTRY_DSN=logdate-sentry-dsn:latest
```

The Cloud Run rollout is a fresh revision; existing in-flight requests
finish on the old revision. After ~30 s the new revision is serving 100%
of traffic and `Napier.i("Sentry initialised for production")` should
appear in Cloud Logging at boot.

## Verifying the wiring

After provisioning, two ways to confirm events are flowing:

1. **Boot log line.** `Sentry initialised for production` (or
   `for development`) appears in Cloud Logging once per revision rollout.
2. **Test event.** Hit a deliberately broken endpoint (any 500 path) and
   confirm the event appears in the Sentry dashboard within ~30 s. The
   event should carry `environment: production` and a stack trace.

If neither fires, the most likely causes:

- DSN typo — `Sentry.init` accepts any non-empty string and silently
  no-ops on a malformed DSN. Cross-check with
  `gcloud secrets versions access latest --secret=logdate-sentry-dsn --project=$PROJECT`.
- Runtime SA missing the `secretmanager.secretAccessor` role — the
  Cloud Run revision will fail to start with "Permission denied on
  secret"; visible in `gcloud run revisions describe`.
- Sentry project rate-limited — visible in the project's Stats &
  Quotas page.

## Soft warning when unset in production

If `SENTRY_DSN` is empty in a production runtime, `initializeSentry` logs a
Napier.w warning at boot ("SENTRY_DSN unset in production — …") and the
server continues without Sentry. Losing observability depth isn't a
security failure the way a missing JWT secret is, so
`ProductionConfigValidator` doesn't fail-fast on it.

## Rotating the DSN

DSN rotation in Sentry is "regenerate the project key in the dashboard."
After regenerating:

```bash
echo -n "$NEW_DSN" \
  | gcloud secrets versions add logdate-sentry-dsn \
      --project="$PROJECT" --data-file=-

gcloud run services update "$SERVICE" \
  --project="$PROJECT" --region="$REGION" \
  --update-secrets=SENTRY_DSN=logdate-sentry-dsn:latest
```

The old key continues working until you destroy it in the Sentry
dashboard, so there's no overlap window during which events are dropped.
