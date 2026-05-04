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

If `SENTRY_DSN` is empty in a production runtime, `initializeSentry` in
`Application.kt` logs a Napier.w line:

```
SENTRY_DSN is unset in production — boot continues but uncaught exceptions
and route errors won't be captured anywhere except Cloud Logging.
Provision the secret to close the gap.
```

Boot still proceeds. Sentry being unconfigured isn't fatal — losing it
loses observability depth, not the server itself. The whole point of the
fail-fast story in `ProductionConfigValidator` is to refuse to start when
*security* is broken (placeholder secrets, missing JWT key); Sentry isn't
in that category.

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

## Future direction

- **Tracing/APM** — `tracesSampleRate` is left at the default `0.0`. If
  the team starts caring about per-request performance breakdowns,
  bumping it to `0.05` or `0.1` plus enabling the Ktor integration
  (`io.sentry:sentry-ktor`) gives transaction traces. Not done today
  because errors and breadcrumbs are sufficient and APM data is the
  expensive part of a Sentry plan.
- **PII scrubbing** — Sentry's default scrubbers cover the obvious
  stuff (Authorization headers, cookies). If we ever start surfacing
  passkey credential data or DIDs in error contexts, configure
  `sendDefaultPii = false` and explicit scrubbers for those fields.
- **Release tracking** — `release` is hardcoded to
  `logdate-server@1.0.0`. Once we cut versioned releases, source it from
  the Cloud Run revision name or a `LOGDATE_RELEASE` env var so Sentry
  groups regressions correctly across deploys.
