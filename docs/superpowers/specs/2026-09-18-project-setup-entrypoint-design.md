# `./run setup`: one idempotent entrypoint for setup and deployment prep

**Status:** implemented (phases 1 and 2). User-facing reference:
[`docs/reference/project-setup.md`](../../reference/project-setup.md).

## Problem

Setup was spread across about 25 scripts, only two of them reachable from
`./run`, and nothing reported the state of a machine or an environment.
Observed consequences: a worktree without `local.properties`, a stale debug
Firebase secret in CI, Play publishing never configured, and `core.hooksPath`
pointing at a deleted repository, so no commit hooks ran.

## Decisions

- **Single entrypoint:** `./run setup [local|staging|production]`, with
  `--check`, `--only`, `--skip`, `--list`, `--non-interactive`, and
  `--enable-play-production`. `./run` now forwards arguments and shows its
  Setup category.
- **Step contract:** each step is a file with `describe`, a read-only `check`
  (0 ok / 1 fixable drift / 2 needs a person), an idempotent `apply`, and
  optional `requires`. The runner re-checks after every apply, so "applied"
  means "seen working". The exit status is 0, 1, or 3 (waiting on a person).
- **Orchestrate, don't rewrite:** steps call the existing purpose-built
  scripts. New shared code lives in `scripts/lib/common.sh` and
  `scripts/lib/google-api.sh`.
- **Per-environment constants** are committed in
  `scripts/setup/environments/<env>.conf`. `validate-firebase-config.sh` reads
  its ids from there.
- **No extra CLIs:** Firebase and Play are called over REST with the existing
  `gcloud` login.
- **No service-account keys:** the organisation forbids them
  (`iam.disableServiceAccountKeyCreation`). CI publishes to Play as
  `play-publisher` through Workload Identity Federation, and Gradle Play
  Publisher uses application-default credentials. Setup verifies Play access by
  impersonating the account through Service Account Token Creator.
- **GitHub `production` environment:** no required reviewer. A deployment
  policy allows only `main`, `android-v*`, and `server-v*`. With one
  maintainer, a reviewer gate only paused every dogfood publish.
- **Dogfood** is the Play internal track from `main`; **production** promotes
  the same bundle from an `android-v*` tag. Both use `cloud.logdate.app`,
  whose allowlist already trusts Play's app-signing certificate.
- **Secret drift** is detected through `<SECRET>_SHA256` variables that
  fingerprint high-entropy files only, never passwords. Every secret value
  reaches its command on stdin.
- **CI** runs `./run setup --only test-deps` and `scripts/tests/run-all.sh`.
  Before this, no script test ran anywhere.

## Out of scope (phase 3)

Unifying `setup-gcp-deploy.sh`, `bootstrap-gcp-fresh.sh`, and
`deploy-production.sh`; remote Terraform state; iOS signing secrets; Sentry;
OAuth clients; Maps keys.
