# Launch readiness status

This page records launch gates that are useful to contributors. It intentionally omits account
identities, provider-assigned IDs, certificate fingerprints, deployment revisions, workflow run
IDs, local worktree details, and other operator-specific evidence. Maintainers should verify live
provider state in the relevant console rather than copying values from repository documentation.

The detailed launch criteria remain in
[`docs/superpowers/specs/2026-08-01-logdate-launch-readiness-design.md`](../superpowers/specs/2026-08-01-logdate-launch-readiness-design.md).

## Google and Firebase

Firebase, Google authentication, and Cloud Run setup that can be completed without Google Play
Developer access has been completed for both debug and release environments:

- CI materializes environment-specific Firebase configuration from repository secrets.
- CI receives the debug and release Google ID-token audiences from repository variables.
- Google Auth Platform has web/server and upload-signing Android OAuth clients for the shipping
  Android package.
- Staging and production deployment workflows passed after configuration was refreshed.
- Temporary one-off production services were removed; only the canonical production service
  remains.

The remaining Android Google work requires Play Developer access:

1. Create or access the Play app.
2. Obtain the Play App Signing SHA-1 and SHA-256 certificates.
3. Create the matching Android OAuth client.
4. Add the SHA-256 certificate to Digital Asset Links.
5. Complete a store release and verify sign-in from the Play-installed build.

See [`docs/runbook/release-secrets.md`](../runbook/release-secrets.md) for the operator procedure.
That runbook names configuration variables but does not contain their values.

## Other launch gates

- **Screenshot review:** the screenshot suite runs, but changed, missing, and blank references still
  require visual review. Do not bulk re-record references; compare current renders against their
  references and confirm that each scene contains meaningful content.
- **Feature readiness:** incomplete features must be explicitly reviewed and either completed or
  placed behind the existing feature-flag boundary.
- **Picture-in-picture:** activity lifecycle coverage exists; visual behavior still belongs in the
  screenshot review.
- **Rewind:** automated criteria are recorded in
  [`rewind-launch-criteria.md`](rewind-launch-criteria.md). Device, platform, seeded-account, and
  human-sign-off criteria remain manual.
- **Desktop screenshots:** these require a real display and are not enforced by headless CI.
- **Passkey management:** the server exposes passkey metadata, but the client does not yet display
  all of it.
- **Onboarding:** the remaining scaffold hardening must be re-derived against the current screens.

## Publishing boundary

Android and iOS internal publishing stay disabled until their repository enablement variables are
explicitly set. Production server deployment remains independently gated by release tags.
