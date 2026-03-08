# Migration Strategy

This document describes the migration strategy for the currently implemented AT Protocol identity and PDS-compatible slices.

## Principles

- Existing LogDate auth and sync behavior must remain shippable on `main`.
- AT Protocol identity fields are added without breaking current first-party clients.
- Hosted multi-user users should default to `did:plc`.
- Hostname-level `did:web` remains valid for dedicated deployments.
- Path-based `did:web` is not migrated or supported.

## Implemented Migration Stages

## Stage 1: Schema Expansion

Database changes add:

- `accounts.did`
- `accounts.handle`
- `accounts.signing_key_public`
- `signing_keys` table

These fields are additive and can coexist with the pre-existing account and sync model.

## Stage 2: Account Model Expansion

Server, shared-model, and client-domain account models gain:

- `did`
- `handle`

This allows first-party clients to start storing identity metadata without changing their authentication path.

## Stage 3: Identity Backfill

On startup, `AtprotoIdentityService.backfillMissingIdentities()`:

- normalizes any existing DID or handle values
- provisions missing handles
- provisions hosted DIDs using the configured hosted DID method
- ensures an active signing key exists

This stage is idempotent by design.

## Stage 4: Additive Routes

New routes are added without removing existing ones:

- `/.well-known/atproto-did`
- `/.well-known/did.json`
- OAuth discovery and auth endpoints
- XRPC identity and repo routes
- `POST /api/v1/identity/signing-key/export`

The existing auth and sync endpoints remain intact.

## Stage 5: Compatibility Repo Adapter

The initial PDS slice maps AT Protocol repo requests onto the current LogDate content storage via `AtprotoContentRecordStore`.

This means:

- AT Protocol clients can read and write the compatibility collection now
- the existing sync/content storage remains the source behind that adapter for now
- the final MST/CAR-backed repo migration is still future work

## Hosted DID Method Strategy

### Hosted multi-user default

Use `did:plc`.

Reason:

- matches AT Protocol hosting expectations better
- avoids invalid path-based `did:web` designs
- gives a portable public identity shape for hosted users

### Dedicated or custom-domain deployments

Use hostname-level `did:web` where that deployment model makes sense.

## Operational Notes

## First-party client compatibility

- the LogDate app continues using its current passkey + bearer-JWT flow
- DID and handle fields are additive metadata
- no first-party forced OAuth migration is required

## Third-party interoperability

- OAuth + DPoP is additive
- XRPC repo endpoints are additive
- third-party clients can start integrating without breaking first-party clients

## Current non-persistent areas

The current OAuth authorization service stores:

- PAR requests
- authorization codes
- refresh tokens

in memory.

That is acceptable for the current standalone server slice, but it is not yet the final clustered or durable deployment story.

## Rollback Expectations

Because the current AT Protocol work is additive:

- existing auth endpoints can remain enabled even if OAuth routes are disabled
- existing sync endpoints can remain enabled even if XRPC repo routes are disabled
- identity fields can remain populated without forcing AT Protocol client usage

Rolling back should not require inventing an invalid `did:web` shape.

## What Is Still Future Migration Work

- durable OAuth storage
- PLC update and recovery flows
- user-controlled rotation keys
- canonical MST/CAR repo persistence
- broader LogDate lexicon and multi-collection repo migration
- federation surfaces such as relay or firehose
