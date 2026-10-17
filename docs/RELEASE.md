# Release and recovery

This is the overview of how each part of LogDate reaches users and how to back a release out. Each
section names the candidate and production path, and links the detailed reference that maintainers
follow. Every release starts from `main`: a change ships only after its pull request is merged and
CI passes.

The Play and App Store publishing paths stay off until a maintainer enables each one with a
repository variable, so `main` stays shippable before signing keys and store access exist. `./run setup production` creates the
secrets, environments, and variables these paths need; [Release secrets](runbook/release-secrets.md)
and [Staging and production configuration](runbook/staging-production-configuration.md) are the
operating contracts.

## Release paths

| Part | Candidate | Production | Trigger for production |
| --- | --- | --- | --- |
| Server | Staging on Cloud Run | Production on Cloud Run | `server-v*` tag on a commit already deployed to staging |
| Android phone and Wear OS | Play internal testing track | Play production, in stages | Manual workflow run naming a tested `android-v*` tag |
| iOS | TestFlight internal build | App Store review | `ios-v*` tag, after approval in the `ios-production` environment |
| AT Protocol libraries | Local publication | GitHub Packages, and Maven Central when configured | `atproto-v*` tag |
| Desktop | Local packages only | Not published | None |

### Server

After CI passes on `main`, the server deploys to staging (`cloud-staging.logdate.app`) on its own.
Production (`cloud.logdate.app`) deploys only from a `server-v*` tag. The production workflow first
confirms that the same commit deployed to staging, that staging's health check reaches its database,
and that the passkey smoke test passes against staging. It then runs the database migrations
explicitly, deploys a candidate revision that receives no traffic, smoke-tests it, moves all traffic
to it, and verifies the public service.

Before tagging, follow the server [pre-release checks](runbook/staging-production-configuration.md#pre-release-checks).

### Android phone and Wear OS

The phone and watch apps share one Play listing. A maintainer runs **Publish Android to Play** with
the `internal` stage from `main` to put a signed release bundle on the internal testing track; this
is the release candidate. Production never rebuilds: after the candidate passes the
[Android public release acceptance](launch/android-public-release.md) matrix, the maintainer tags
the tested commit `android-v<major>.<minor>.<patch>` and promotes that exact Play version code
through the `start-5`, `advance-25`, and `complete-100` stages, reviewing crash, ANR, backup,
sign-in, and support signals for at least 24 hours at each stage.

[Google Play publishing](reference/google-play-publishing.md) covers the workflow, versioning, and
Wear OS details.

### iOS

Each push to `main` uploads a TestFlight internal build. An `ios-v*` tag re-archives the app from
source, because App Store Connect cannot promote a TestFlight build, and submits it for App Store
review once a reviewer approves the `ios-production` environment. See
[iOS App Store publishing](reference/ios-publishing.md).

### AT Protocol libraries

An `atproto-v*` tag publishes the standalone AT Protocol libraries to GitHub Packages, and to Maven
Central when its signing secrets are configured. See [ATProto publishing](reference/atproto-publishing.md).

### Desktop

No workflow publishes the desktop app. Desktop packages built with `./run build:desktop` are local
verification artifacts until a signed desktop release path exists.

## Acceptance prerequisites

A release candidate is accepted only when all of these hold:

- The commit is on `main`, its pull request was merged with a rebase, and CI passed for it.
- The candidate was observed working in its candidate environment, not only built: staging for the
  server, the Play internal track for Android, and TestFlight for iOS.
- For Android, every row of the [acceptance matrix](launch/android-public-release.md) is closed for
  that exact Play version code, with no open data-loss or release-blocking defect.
- The platform's pre-release checks pass, and for Play and the App Store, the publishing path's
  repository variable is enabled.

A successful upload, build, or health check alone does not accept a release.

## Recovery

### Server

If any step after the traffic switch fails, the deploy workflow moves traffic back to the revision
that was serving before. To back out a production release that already succeeded, move traffic
back to the previous revision:

```bash
gcloud run revisions list --service logdate-server --project logdate --region us-central1
gcloud run services update-traffic logdate-server --project logdate --region us-central1 \
  --to-revisions <previous-revision>=100
```

Then fix forward with a new `server-v*` tag that goes through staging again. A traffic rollback
does not undo database migrations or the data the new revision wrote, because migrations run before
the candidate and are never reversed automatically. The previous revision must still work with the
migrated schema; if it does not, recovery is a forward fix, not a rollback.

### Android phone and Wear OS

Play cannot reinstall an older version code. To stop a bad release, run **Publish Android to Play**
with `halt` and the same tag and version code: new installations stop, while devices that already
updated keep the release. Record the incident and the recovery decision, then either resume the
same release with `resume-5` or `resume-25`, or ship a fix as a new internal candidate with a new
acceptance pass, version code, and tag.

### iOS

A build waiting for review can be withdrawn in App Store Connect. A released build cannot be rolled
back; ship a fixed build with a higher version through a new `ios-v*` tag.

### AT Protocol libraries

Published versions are immutable. Fix a bad release by publishing a new patch version.

## Abort conditions

- **Server:** the production deploy stops before any traffic moves if the commit never deployed to
  staging, staging fails its health or passkey checks, a migration fails, or the candidate fails
  its smoke tests.
- **Android:** halt promotion on a data-loss, restore, sign-in, or material stability regression,
  and record the stop decision and recovery action before resuming.
- **iOS:** withhold `ios-production` approval, or withdraw the submission, when the TestFlight build
  for that commit shows the same kinds of regression.
