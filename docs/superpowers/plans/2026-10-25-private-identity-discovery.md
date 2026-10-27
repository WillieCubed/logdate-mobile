# Private identity discovery implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Public discovery reveals an account's identity only after its owner has explicitly made that DID public.

**Architecture:** Store the public DID separately from the current account DID. Public discovery requires a complete, active identity whose DID matches that consent record. Internal lookups continue to support private accounts. Ordinary account saves cannot change the public DID. This repair adds no publication action; the later publication flow must establish informed consent and complete publication before recording the public DID.

**Tech Stack:** Kotlin, Ktor, Exposed, PostgreSQL/Flyway.

**Spec:** `docs/superpowers/specs/2026-10-25-atproto-interop-readiness.md`, Decisions and Direction A publication/handle-resolution requirements.

## Global constraints

- Identities are private by default; no existing account is grandfathered into public discovery.
- A deployment-wide publishing setting is not owner consent.
- Do not publish to the PLC directory or change recording, storage, credentials, or journal data.
- Use existing repository and route patterns; preserve outage responses.

## Review focus

- Consent for a previous DID cannot expose a replacement identity.
- Unauthenticated repository descriptions cannot bypass the handle restriction.
- A private `did:web` account must not expose its DID document.
- An ordinary account save must not grant or revoke public discovery.
- Server-wide DID discovery and internal account lookups must continue to work.

### Task 1: Protect public discovery

**Files:** Account model/repositories, AccountsTable and migration, AtprotoIdentityService, IdentityRoutes; route and repository tests; handle-resolution reference.

**Interfaces:**
- `Account.publicIdentityDid: String?` records the exact identity made public; null means private.
- `AtprotoIdentityService.findPublicByHandle(handle: String): Account?` is for anonymous discovery. `findByHandle` remains internal.
- `AccountRepository.recordPublicIdentity(accountId: Uuid, expectedDid: String): Boolean` is the trusted publisher's conditional storage write, with no exposed API route.

- [x] Add route regressions for private PLC and Web identities, proving matching missing-handle responses and unchanged internal lookups.
- [x] Run those tests against the existing implementation and observe behavior failures.
- [x] Add the nullable database field without backfilling it. Preserve it on ordinary saves and conditional identity updates.
- [x] Require matching consent on public handle lookup, account DID documents, and repository descriptions.
- [x] Add public fixtures and tests for changed DIDs, inactive accounts, repository recreation, and save isolation.
- [x] Run affected server identity, route, and repository tests, server lint/quality checks, and server-client sign-in integration tests.
- [x] Update the handle-resolution reference with the publication precondition and migration trade-off.
- [ ] Review the concrete diff with the owner before committing. Use the required atomic staging/commit chain and request authorization before push/PR creation.

## Trade-offs

The migration deliberately hides previously resolvable identities until the owner publication flow exists. PLC operations or recovery-key registration alone do not establish consent. Binding consent to a DID avoids accidentally publicizing an identity adopted later. Retaining the public DID after an identity change preserves the historical consent record without making the replacement identity public. This is an exposure gate, not an implementation of irreversible publication or revocation of data already copied by outside services.
