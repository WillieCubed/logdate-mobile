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
Making that page work with a passkey covers every such app at once. Today what an app can then do is
confirm the identity: no collection is open to apps yet. Later it may read and write collections
that are opened to apps on purpose. It never sees or changes the LogDate journal.

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
| `atproto` | Confirming which account signed in. Nothing in the repository, read or write. |
| `atproto transition:generic` | Also reading and writing collections that are open to apps. |

A collection is open to apps only when it is listed on purpose in the server's allowlist
(`OAuthScopes.collectionsOpenToApps`), compared in canonical NSID form so a differently cased name
cannot slip past. **No collection is open yet.** Everything the repository holds today is LogDate
journal data, and the `studio.hypertext.logdate.*` and `app.logdate.*` lexicons can never be added:
the server refuses to start with one on the list.

So, whatever the scope, an app signed in through OAuth today:

- sees every collection as empty and every record as not found;
- is refused, with `403`, when it writes a record or uploads a file;
- cannot export the repository or read its latest commit or status, because those carry every
  journal record;
- cannot download blobs, until blobs record which app uploaded them.

Uploads stay closed even with `transition:generic`. An app could only fill the account's storage with
files nothing can refer to or remove, so uploads wait until a collection open to apps refers to
files. At that point uploads also need a size limit, a streamed body and a place in the account's
storage quota.

`com.atproto.repo.describeRepo` answers anyone without a sign-in, so it only lists collections that are
open to apps. The LogDate apps never use OAuth tokens, so none of this changes them.

## How long access lasts

- Access tokens last 15 minutes, because they cannot be revoked.
- Refresh tokens last 14 days for apps that are not on the trusted list
  (`LOGDATE_OAUTH_TRUSTED_CLIENT_IDS`) and 30 days for trusted ones.
- Removing a passkey or deleting the account revokes every grant the account has made. Account
  deletion does this inside `AccountDeletionService`, so every way of deleting an account gets it.
  Every app loses access within the access token's lifetime.
- A list of connected apps, with a way to revoke one, is a separate feature and is not part of
  this design.

Revocation has to hold even against a refresh that is already running. Every grant records when the
person approved it, and revoking records the revocation time for the account
(`oauth_subject_revocations`). Then:

- refreshing replaces the old refresh token in one transaction. It locks the token's row, checks
  that it is unrevoked, unexpired and approved after the account's last revocation, deletes it and
  saves the replacement. The access token is only returned when this succeeds;
- two refreshes of one token therefore cannot both succeed: the second waits for the row lock, then
  finds the token gone;
- if a refresh and a revocation interleave, the replacement token keeps its grant's approval time,
  which is before the revocation, so it can never be used. The worst case is one access token that
  expires within 15 minutes;
- revoking also deletes the account's unredeemed authorization codes, and redeeming a code saves
  its refresh token only if the grant was approved after the last revocation;
- redeeming an authorization code locks and deletes its row in one transaction, so two exchanges of
  one code cannot both succeed.

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
- says what continuing allows: that the app learns which LogDate account this is and can't see or
  change anything in the account. That is true for both scopes today, because nothing is open to apps;
  the sentence has to change when a collection opens;
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
   cannot approve another. The options never list credential IDs, even when the `login_hint` names a
   local account, so pushing a request for someone's handle reveals nothing about their passkeys. The
   browser offers the person's discoverable passkeys, which is what LogDate creates. Nothing about the
   account is created or changed before the passkey is verified.
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

Both passkey endpoints are limited to 10 attempts a minute per client address, the same limit as the
apps' passkey sign-in, and answer `429` with `Retry-After` beyond that.

The client address comes from `X-Forwarded-For`, and only from the entry a trusted proxy added. Cloud
Run's front end appends the address it received the request from, so the server reads the last entry;
anything before it came from the client and may be invented. `TRUSTED_PROXY_HOPS` counts further
trusted proxies, such as a load balancer in front of Cloud Run. The apps' sign-in and device-link
limits use the same address; before this they were keyed on the proxy, so every visitor shared one
limit.

### Server errors

A database failure is the server's problem, not the caller's. It is logged with the full error and
answered as `503 temporarily_unavailable`, or `500 server_error` for anything else unexpected, with a
fixed message and never SQL text. Specifically:

- a DPoP check that cannot reach the replay store is a `503`, not a failed sign-in;
- the authorization page shows "Sign-in isn't available right now" rather than "This sign-in link
  has expired". "Expired" is only shown for a request that is unknown or past its time.

### Replays

- Each DPoP proof is accepted once: the server remembers each proof's key and `jti` for as long as
  its `iat` stays acceptable.
- Each PKCE `code_challenge` starts one pushed request; a reused one is refused for an hour.

Production runs more than one server instance, so a replay can reach a different instance from the
first use. Both kinds of value are recorded in one PostgreSQL table that every instance shares
(`oauth_used_values`):

- each row is the SHA-256 of the namespaced value and when it expires, so the values themselves are
  never stored;
- recording a value is a single insert that does nothing when the value is already there, so two
  instances racing on the same value cannot both accept it;
- each insert also deletes up to 100 expired rows, which keeps the table small without a separate
  job. The delete re-checks expiry, so a value another instance has just recorded again is left
  alone.

Used client assertions (`private_key_jwt` `jti` values) are recorded in the same table until the
assertion expires.

A server running without a database keeps the same records in memory instead, with each value
expiring on its own lifetime.

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
  - how trusted and untrusted apps are named, and the access sentence;
  - the flag being off;
  - a `client_id` mismatch, an expired request and a server failure;
  - passkey options that never list credentials;
  - approval with a verified assertion;
  - a challenge used for another request or for an app sign-in;
  - a second approval of the same request;
  - a `login_hint` mismatch, which leaves the request usable;
  - a tampered assertion and a passkey from another origin;
  - cancelling;
  - rate limits keyed on the forwarded client address, for the page and for app sign-in, including an
    invented leading address;
  - revocation when a passkey is removed and when the account is deleted;
  - startup refusals;
  - the `iss` parameter on every redirect;
  - database failures answered as server errors without internal details.
- XRPC tests cover:
  - empty lists and missing records for collections not open to apps;
  - `app.logdate.journal.entry` and `studio.hypertext.logdate.*`, in any case, staying closed even when
    the repository accepts them;
  - a collection opened in a test being readable and writable;
  - reads and writes needing `transition:generic`;
  - refused uploads, export, commit, status and blob reads;
  - deactivated accounts;
  - `describeRepo`;
  - refusing to open a journal collection.
- Grant tests cover:
  - dropped scopes;
  - reused PKCE challenges;
  - a lost race to decide a request;
  - token lifetimes;
  - revocation;
  - DPoP proof replay;
  - a client assertion replayed through another server instance;
  - per-entry expiry in the in-memory replay store.
- Replay store tests check that a value recorded through one store is refused through another
  sharing the database, that only hashes are stored and that expired rows are removed.
- On the PostgreSQL that CI provides, database tests check that:
  - 16 instances racing on one value accept it exactly once;
  - 12 parallel refreshes of one token let exactly one through;
  - a refresh racing a revocation never leaves a usable token;
  - 12 parallel exchanges of one authorization code redeem it once.
- End-to-end tests in `integration/server-client-e2e` start the real server, with strict passkey
  verification, and a separate third-party app that publishes its own client metadata document.
  - They create a passkey-only account with a synthetic authenticator and confirm it has no password.
  - The test plays the browser through the page and the passkey ceremony. The app exchanges the code
    with PKCE and DPoP and resolves the token's subject to the account's handle. Its upload is
    refused.
  - With `atproto` alone, repository reads are refused.
  - Against a journal seeded through the apps' sync API, the app sees an empty content list, a draft
    that is not found, and a refused repository export. Its delete of an entry and its write to
    `app.logdate.journal.entry` are refused, and the entry is still in the journal afterwards.
