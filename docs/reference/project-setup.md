# Project Setup (`./run setup`)

`./run setup` prepares a machine to build and test LogDate, and prepares an
environment for deployment. It runs the repository's purpose-built scripts in
the right order.

```bash
./run setup                  # this machine and this checkout
./run setup staging          # ...then the staging environment
./run setup production       # ...then production, including Google Play
./run setup --check          # only report; change nothing
```

## The idea: checks that can fix themselves

Setup is a list of **steps**. Each step describes one thing that should be
true, such as "the git hooks are installed" or "CI has the current Firebase
config", and knows two things:

- how to **check** whether it is true, without changing anything, and
- how to **apply** a fix when it is not.

For every step, setup runs the check first. If the check passes, nothing
happens. If it fails and the step can fix it, setup applies the fix and checks
again, so a step counts as done only once it has been seen working. On a
machine that is already set up, `./run setup` changes nothing.

Steps that cannot be automated, such as inviting a service account into Play
Console, print exactly what to do and wait for Enter before checking again.

At the end you get a summary:

| Mark | Meaning |
|---|---|
| ✓ ok | Already correct |
| ↻ applied | Was wrong; setup fixed it and confirmed the fix |
| ✗ drift / failed | Wrong, and not fixed (with `--check`, or because the fix failed) |
| ✋ manual / blocked | Needs a person, or waits on a step that does |

The exit status is `0` when everything is ok, `1` when something is wrong, and
`3` when only manual actions remain.

## Options

| Option | Effect |
|---|---|
| `--check` | Report drift without changing anything. |
| `--only a,b` | Run only these steps, e.g. `--only hooks,local-properties`. |
| `--skip a,b` | Run everything except these steps. |
| `--list` | Show the steps for the target and exit. |
| `--non-interactive` | Never prompt or wait. Manual steps are reported and skipped. CI uses this. |
| `--enable-play-production` | Also turn on promotion to the Play production track. Off unless you ask. |

## Steps

### Every machine (`./run setup`)

| Step | Makes sure that… |
|---|---|
| `tools` | git, Java, and jq are installed (plus gh, gcloud, curl, and keytool for environments). |
| `auth` | For environments: you are logged in to `gh` and `gcloud`. Setup never logs in for you. |
| `hooks` | Git runs the repository's hooks in `.githooks`, which validate commit messages. |
| `local-properties` | `local.properties` exists and points Gradle at your Android SDK. In a `git worktree` it is copied from the main checkout. |
| `firebase-configs` | The real `google-services.json` files are in place. Without them Gradle writes a stub, and crash reporting silently does nothing. |
| `test-deps` | The passkey verifier's Python venv is installed, so `scripts/tests/run-all.sh` can run. |

### An environment (`./run setup staging|production`)

| Step | Makes sure that… |
|---|---|
| `gh-environment` | The GitHub environment exists, and only the branches and tags in its definition file can deploy with it. There's no approval click, but pull requests can never read its secrets. |
| `signing-keys` | The environment's upload keystore is in `~/.logdate-signing`, readable only by you, and its certificate matches `infra/android-signing/<env>-signer-evidence.json`. |
| `firebase-apps` | Firebase has an Android app for every package the builds use, with the signing certificates registered. |
| `firebase-ci-config` | CI's copy of the Firebase config matches yours. |

### Google Play (`./run setup production`)

| Step | Makes sure that… |
|---|---|
| `play-service-account` | The `play-publisher` service account exists and the Play API is enabled. It has no key: GitHub Actions impersonates it through Workload Identity Federation, and setup's own checks impersonate it as your `gcloud` account. |
| `play-access` | That account can publish the app. **Manual once:** invite its email in Play Console. |
| `play-keystore` | The `production` GitHub environment holds the upload keystore and its passwords. |
| `play-release-tag` | An `android-v<x.y.z>` tag exists, which version codes are derived from. Asks before pushing one. |
| `play-first-bundle` | Play has at least one bundle. **Manual once:** setup builds and verifies it, and you upload it. |
| `play-publishing` | Every push to `main` publishes to the internal testing track ("dogfood"). |

## How releases flow once setup is done

- **Dogfood:** every push to `main` builds a release bundle, signs it with the
  upload key, and publishes it to the Play **internal testing** track. Testers
  on that track get it from the Play Store within minutes.
- **Production:** pushing a tag such as `android-v1.2.0` *promotes* that exact
  internal build to the production track. It is never rebuilt, so what you
  tested is what ships. Turn this on with `--enable-play-production`.

Both tracks talk to `https://cloud.logdate.app`. That works because Play
re-signs every install with its app-signing key, and the production server
already trusts that key for passkey sign-in.

## Keeping secrets secret

Several steps handle keys and passwords, so they follow three rules:

- Values reach commands on **stdin** or through files, never as command-line
  arguments, which any process on the machine can read.
- Scripts that hold passwords switch off `bash -x` tracing for themselves.
- GitHub never returns a secret's value, so to notice when a secret is out of
  date, setup records the **SHA-256 of the file** it uploaded in a variable
  named `<SECRET>_SHA256`. Only high-entropy files (keystores, configs) are
  fingerprinted, never passwords.
- Google Cloud access uses no service-account keys. The organisation policy
  `iam.disableServiceAccountKeyCreation` forbids them, and CI authenticates
  through Workload Identity Federation instead.

## Where things live

| Path | What it holds |
|---|---|
| `scripts/setup/main.sh` | The entrypoint: options, and which steps each target runs. |
| `scripts/setup/runner.sh` | Runs steps: check, apply, re-check, summary. |
| `scripts/setup/steps/<id>.sh` | One file per step. |
| `scripts/setup/environments/<env>.conf` | Per-environment constants: projects, app ids, backend URL, allowed refs. No secrets. |
| `scripts/lib/common.sh` | Logging and prompt helpers shared by scripts. |
| `scripts/lib/google-api.sh` | Firebase and Play REST calls using your `gcloud` login. |

## Adding a step

1. Create `scripts/setup/steps/<id>.sh`. Name the functions after the id with
   dashes turned into underscores, so `my-step` defines `my_step_describe`.
2. Define:
   - `<fn>_describe`: one line saying what should be true.
   - `<fn>_check`: **read-only**. Return `0` if ok, `1` if `apply` can fix
     it, or `2` if a person must act. Print why whenever it isn't `0`.
   - `<fn>_apply`: make it true, safely re-runnable. Prefer calling an
     existing script over copying its logic. Return `2`, with instructions,
     if it did what it could and a person must finish.
   - `<fn>_requires` (optional): the step ids that must be ok first.
3. Add the id to the right list at the top of `scripts/setup/main.sh`.
4. Test it with a fake CLI, as `scripts/tests/setup/test-setup-gh-environment.sh`
   does, and run `./scripts/tests/run-all.sh`.

## Not covered yet

Server infrastructure on Google Cloud (`./run deploy:bootstrap`,
`./run deploy:production`), iOS signing, Sentry, and OAuth clients still have
their own scripts. They will move into `./run setup` in a later round.
