# Neon-Backed First-Party Database Design

**Date:** 2026-08-01  
**Status:** Approved by the product owner through the explicit Neon correction  
**Scope:** LogDate Cloud staging and production relational persistence

## Decision

LogDate Cloud staging and production use Neon PostgreSQL through the server's
standard `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD` contract.
Cloud SQL is not provisioned, migrated, or required by the first-party deploy
path.

Cloud Run remains the first-party compute platform and durable object storage
remains independent of PostgreSQL. Compatible/self-hosted LogDate servers may
use any PostgreSQL service that satisfies the same standard connection
contract; first-party infrastructure does not carry a second database provider
solely for that possibility.

## Environment isolation

- Staging and production use separate Neon projects or isolated branches with
  independent credentials and no shared writable database.
- Each environment stores three distinct secrets: a credential-free PostgreSQL
  URL, user, and password.
- Official deployment contracts pin exact enabled numeric secret versions.
  `latest` is allowed only in the temporary recovery path and is removed after
  the first authoritative contract deployment.
- Runtime startup, Flyway migrations, schema validation, health checks, smoke
  users, sync data, and backups all target the same environment contract.
- A debug Android build defaults to staging and a release build defaults to
  production; neither build embeds database credentials.

## Connection safety

The connection URL may use `postgres://`, `postgresql://`, or
`jdbc:postgresql://` and is normalized once before any database client starts.
It must identify one host and one database, contain no embedded user/password,
and contain no fragment, control characters, or multi-host authority. The only
launch-supported query parameters are `sslmode` and `channelBinding`, with
values validated before Flyway or PostgreSQL verification runs.

Flyway and `psql` receive the same normalized host, port, database, principal,
password, TLS mode, and channel-binding policy through permission-0600 temporary
environment files removed on every exit. Secret values and the complete URL may
not appear in argv, logs, traces, generated commands, or GitHub outputs.

## Deployment and recovery

Every rollout runs migrations before deploying a no-traffic candidate. Invalid
or unavailable database configuration fails before Docker or any database
mutation. A candidate is promoted only after database-backed internal health,
the exact immutable release SHA, the correct environment origin/RP ID, and the
real passkey signup/signin/cleanup smoke journey pass.

Production recovery uses the same reviewed workflow and contract as normal
deployment. No manual traffic mutation is considered authoritative unless the
equivalent workflow contract is immediately reconciled in source.

## Cloud SQL retirement

The Cloud SQL connector dependency, Terraform resources, migration proxy,
bootstrap behavior, IAM grants, and provider-specific documentation are removed
from the first-party repository path. Before deleting a live Cloud SQL instance,
operators inventory its databases, row counts, backups, consumers, and recent
connections without exposing data. Deletion occurs only after evidence proves
that Neon is authoritative and the instance contains no unique user data.

## Acceptance evidence

The database slice is complete only when all of the following are current:

1. Script tests prove safe URL normalization, rejection-before-mutation, exact
   Flyway/`psql` parity, secret non-disclosure, and no Cloud SQL proxy use.
2. Terraform/render tests prove staging and production pin independent Neon
   secret versions and provision no Cloud SQL resources or roles.
3. Server tests prove production accepts the standard PostgreSQL contract and
   rejects missing or ambiguous database configuration.
4. A staging deployment migrates Neon, deploys a no-traffic candidate, completes
   database/passkey/durability smoke, and promotes successfully.
5. Production is healthy on the same contract, and a read/write/read/delete
   durability probe plus a documented database restore drill succeeds.
6. A read-only inventory proves whether any Cloud SQL resource can be safely
   decommissioned.
