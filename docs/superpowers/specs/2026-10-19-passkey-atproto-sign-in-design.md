# Passkey sign-in for other AT Protocol apps

Part of the LogDate on the AT Protocol initiative. The umbrella design is
[LogDate on the AT Protocol](./2026-10-09-logdate-atproto-interoperability-design.md).

Most LogDate accounts sign in only with passkeys. Every LogDate account also has an AT Protocol
identity, such as `alice.logdate.app`, and the point of that identity is that other apps in the
network can use it. This document decides how a person whose account has no password signs in to
one of those other apps, and what such an app may do once it has.

## Where things stand today

There are two ways another app can start a session with a LogDate account.

- **`com.atproto.server.createSession`** takes a handle and a password. It only works for an account
  that set an AT Protocol password, which happens only when the account was created through
  `com.atproto.server.createAccount`. Accounts created in the LogDate apps have no password, so
  this path is closed to them.
- **OAuth.** The server already runs an AT Protocol OAuth authorization server: pushed
  authorization requests (`/oauth/par`), DPoP-bound tokens, refresh, revocation and client metadata
  documents. The step in the middle, where the person proves who they are and agrees, is
  `GET /oauth/authorize` and `POST /oauth/authorize`. Both only accept a LogDate bearer token and
  answer in JSON. A third-party app sends the person's browser to `/oauth/authorize`, and a browser
  has no bearer token, so it gets a `401` with a JSON body. Nobody can finish an OAuth sign-in from
  a browser today, with or without a password.

The OAuth flow has further gaps that matter as soon as a browser can finish it:

- an OAuth token reaches every record in the account's repository. That repository still holds the
  LogDate journal (entries, journals, links, drafts and moments under `studio.hypertext.logdate.*`)
  until the private-storage work moves it into personal spaces, and writes there flow into the
  apps' journal sync;
- `scope` is stored but never enforced, and access tokens last an hour with no way to revoke them;
- the server advertises `authorization_response_iss_parameter_supported`, but the redirect back to
  the app carries no `iss` parameter. AT Protocol OAuth clients compare `iss` with the server they
  started at and stop if it is missing;
- the server does not check that the `client_id` in the browser URL matches the one in the pushed
  request.

## Decision

**Other apps sign in through LogDate's OAuth authorization page, and the person proves who they
are on that page with their passkey.** No password is created or needed.

AT Protocol apps that support OAuth discover the account's authorization server from its DID
document, push an authorization request, and send the person's browser to the authorization page.
Making that page work with a passkey covers every such app at once. What an app can then do is
limited to confirming the identity and, when granted, uploading files and writing its own public
records. It never sees or changes the LogDate journal.

**App passwords are not part of this change.** An app password is a separate secret a person creates
in a signed-in LogDate app and pastes into an older app that only knows `createSession`. It would need
its own storage, its own management screen in the apps, and its own revocation story. That is left
for a later decision, made when someone needs a specific password-only app. Until then, a
passkey-only account cannot use those apps, and no account is asked to create a password.

## What an app may do

The server grants two scopes and drops every other one at the pushed request, so a token's `scope`
lists exactly what is enforced:

| Scope | Allows |
| --- | --- |
| `atproto` | Confirming which account signed in. No writes, no uploads. |
| `atproto transition:generic` | Also writing records and uploading files to the account's repository. |

Whatever the scope, an app signed in through OAuth:

- sees the journal collections (`studio.hypertext.logdate.*`) as empty, and journal records as not
  found;
- is refused, with `403`, when it writes to a journal collection;
- cannot export the repository or read its latest commit or status, because those carry every
  journal record;
- cannot download blobs, until blobs record which app uploaded them.

`com.atproto.repo.describeRepo` answers anyone without a sign-in, so it never lists journal
collections for anybody. The LogDate apps never use OAuth tokens, so none of this changes them.

Today the repository only accepts LogDate's own collections, so a write to any other collection is
refused as unsupported. With `transition:generic` an app can upload files now, and can write public
records once the repository accepts public collections.

## How long access lasts

- Access tokens last 15 minutes, because they cannot be revoked.
- Refresh tokens last 14 days for apps that are not on the trusted list
  (`LOGDATE_OAUTH_TRUSTED_CLIENT_IDS`) and 30 days for trusted ones.
- Removing a passkey or deleting the account revokes every refresh token the account has issued, so
  every app loses access within the access token's lifetime. The server has no sign-out-everywhere
  action today; when it gets one, it should revoke them too.
- A list of connected apps, with a way to revoke one, is a follow-up: it needs a settings screen in
  the apps.

## How it works

### The page

When a browser opens `GET /oauth/authorize?client_id=…&request_uri=…` without a LogDate bearer token,
the server answers with an HTML page instead of the JSON prompt. The JSON prompt for callers that do
send a bearer token is unchanged.

The page:

- names the app. An app on the trusted list is named by its own `client_name` and the web address
  its `client_id` lives at. Every other app is named by its full `client_id` URL, which it cannot
  fake, because a self-reported name can claim to be anything;
- names the account, `@alice.logdate.app`, when the app passed a `login_hint` that matches an
  account on this server;
- says what continuing allows, following the requested scope: with `atproto` alone, that the app
  learns which LogDate account this is and can't see or change anything; with `transition:generic`,
  that it can upload files and can't see or change the journal;
- offers **Continue with passkey** and **Cancel**.

Continuing is the consent. The person picks a passkey in the browser's own passkey sheet, which also
shows which account it belongs to. There is no separate "Allow" step after signing in, so there is no
signed-in browser session and no extra token to protect between the two steps.

**Cancel** posts `decision=deny` and the browser returns to the app with `error=access_denied`.
Cancelling does not need a sign-in: the pushed request is only known to the browser that started it,
and denying it only affects that request.

The page:

- is served with a content security policy that only allows its own inline script and style, marked
  with a per-response nonce;
- cannot be framed, which stops another site from hiding the button under its own;
- is marked `no-store`.

### The ceremony

1. The page calls `POST /oauth/authorize/passkey/options` with the `request_uri`. The server checks
   the request is still valid and issues a WebAuthn challenge of its own type, bound to that
   `request_uri`. An app sign-in challenge cannot approve a request, and one request's challenge
   cannot approve another. When the `login_hint` names a local account, the challenge lists that
   account's credentials, so passkeys that are not discoverable still work. Nothing about the account
   is created or changed before the passkey is verified.
2. The browser runs `navigator.credentials.get` with user verification required.
3. The page posts the assertion to `POST /oauth/authorize/passkey`. The server:
   - verifies the assertion cryptographically, against the bound challenge;
   - loads the account and makes sure it has its DID and handle;
   - checks the account matches the `login_hint`, if there was one;
   - approves the pushed request, which only one decision can ever do.
   The server answers with the app's redirect URI, carrying `code`, `state` and `iss`.
4. The page sends the browser to that URI. The app exchanges the code at `/oauth/token` as it
   already does.

A failure keeps the person on the page with a message they can act on: they cancelled the passkey
sheet, the passkey is not registered to a LogDate account, the passkey belongs to a different account
from the one the app asked for, the request expired and they need to start again in the app, the
server has the page turned off, or there were too many attempts. A mismatch or a failed assertion
leaves the pushed request usable, so the person can try another passkey.

Both passkey endpoints are limited to 10 attempts a minute per network, the same limit as the apps'
passkey sign-in, and answer `429` with `Retry-After` beyond that.

### Replays

- Each DPoP proof is accepted once: the server remembers each proof's key and `jti` for as long as
  its `iat` stays acceptable.
- Each PKCE `code_challenge` starts one pushed request; a reused one is refused for an hour.

Both memories live in the server process and are bounded in size.

### Where passkeys work

A browser only offers a passkey on a page whose host is the passkey's relying-party ID or a
subdomain of it. The authorization page lives at the OAuth issuer. So the feature only works when
the issuer's host is inside `WEBAUTHN_RP_ID`. For example, `cloud.logdate.app` with the relying
party `logdate.app` works.

When the feature is on:

- the issuer's origin is added to the origins passkey verification accepts, so nobody has to list
  it by hand;
- the server refuses to start if the issuer is outside the relying party, or if passkey verification
  is not strict (`WEBAUTHN_STRICT_VERIFICATION`). The message names the values involved, rather than
  the server serving a page whose button can never work or that anyone could approve.

### Feature flag

`LOGDATE_OAUTH_PASSKEY_SIGN_IN_ENABLED`, off by default. Off, the server behaves as it did before: no
page is served, `POST /oauth/authorize` still needs a bearer token, and the two passkey endpoints
answer `501`. The scope enforcement, journal protection, token lifetimes and revocation apply
whether the flag is on or off.

Turn it on per deployment once the issuer and relying party line up there. Staging today does not:
its issuer is `cloud-staging.logdate.app` and its relying party is `staging.logdate.app`.

## What this does not change

- The LogDate apps keep their own passkey sign-in and bearer tokens, and keep full access to their
  own journal.
- OAuth tokens do not reach the LogDate `/api/v1` surface or personal spaces.
- Accounts with an AT Protocol password can still use `createSession`.

## How it is tested

- Route tests cover:
  - the HTML page, its security headers and HTML escaping;
  - how trusted and untrusted apps are named, and the access sentence for each scope;
  - the flag being off;
  - a `client_id` mismatch and an expired request;
  - the passkey options;
  - approval with a verified assertion;
  - a challenge used for another request or for an app sign-in;
  - a second approval of the same request;
  - a `login_hint` mismatch, which leaves the request usable;
  - a tampered assertion and a passkey from another origin;
  - cancelling;
  - the rate limit;
  - revocation when a passkey is removed;
  - startup refusals;
  - the `iss` parameter on every redirect.
- XRPC tests cover:
  - empty journal lists and missing journal records for OAuth apps;
  - refused journal deletes;
  - writes and uploads with each scope;
  - refused export, commit, status and blob reads;
  - deactivated accounts;
  - `describeRepo`.
- Grant tests cover:
  - dropped scopes;
  - reused PKCE challenges;
  - a lost race to decide a request;
  - token lifetimes;
  - revocation;
  - DPoP proof replay.
- End-to-end tests in `integration/server-client-e2e` start the real server, with strict passkey
  verification, and a separate third-party app that publishes its own client metadata document.
  - They create a passkey-only account with a synthetic authenticator and confirm it has no password.
  - The test plays the browser through the page and the passkey ceremony. The app exchanges the code
    with PKCE and DPoP and uploads a file with `transition:generic`.
  - With `atproto` alone, the upload is refused.
  - Against a journal seeded through the apps' sync API, the app sees an empty content list, a draft
    that is not found, and a refused repository export. Its delete of an entry is refused, and the
    entry is still in the journal afterwards.
