# Passkey sign-in for other AT Protocol apps

Part of the LogDate on the AT Protocol initiative. The umbrella design is
[LogDate on the AT Protocol](./2026-10-09-logdate-atproto-interoperability-design.md).

Most LogDate accounts sign in only with passkeys. Every LogDate account also has an AT Protocol
identity, such as `alice.logdate.app`, and the point of that identity is that other apps in the
network can use it. This document decides how a person whose account has no password signs in to
one of those other apps.

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

Two smaller gaps sit in the same flow:

- the server advertises `authorization_response_iss_parameter_supported`, but the redirect back to
  the app carries no `iss` parameter. AT Protocol OAuth clients compare `iss` with the server they
  started at and stop if it is missing;
- the server does not check that the `client_id` in the browser URL matches the one in the pushed
  request.

## Decision

**Other apps sign in through LogDate's OAuth authorization page, and the person proves who they
are on that page with their passkey.** No password is created or needed.

Modern AT Protocol apps, Bluesky's included, sign in with OAuth. Each one discovers the account's
authorization server from its DID document, pushes an authorization request, and sends the person's
browser to the authorization page. Making that page work with a passkey covers every one of those
apps at once.

**App passwords are not part of this change.** An app password is a separate secret a person creates
in a signed-in LogDate app and pastes into an older app that only knows `createSession`. It would need
its own storage, its own management screen in the apps, and its own revocation story. Apps that only
support passwords are the minority and shrinking, so this is left for a later decision, made when
someone needs a specific password-only app. Until then, a passkey-only account cannot use those apps,
and no account is asked to create a password.

## How it works

### The page

When a browser opens `GET /oauth/authorize?client_id=…&request_uri=…` without a LogDate bearer token,
the server answers with an HTML page instead of the JSON prompt. The JSON prompt for callers that do
send a bearer token is unchanged.

The page:

- names the app from its client metadata, and shows the web address its `client_id` lives at so a
  person can tell a real app from a look-alike name;
- names the account, `@alice.logdate.app`, when the app passed a `login_hint` that matches an
  account on this server;
- says what continuing allows, then offers **Continue with passkey** and **Cancel**.

What continuing allows has to be stated honestly. An `atproto` token reaches the account's whole
repository, and today that repository still holds the journal records that the private-storage work
moves into personal spaces. So the page says the app "can read and change the journal data stored in
your LogDate account". When journal records leave the repository, that sentence should narrow to what
remains there.

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
   the request is still valid and issues a WebAuthn challenge from the same passkey service the apps
   use. When the `login_hint` names a local account, the challenge lists that account's credentials,
   so passkeys that are not discoverable still work.
2. The browser runs `navigator.credentials.get` with user verification required.
3. The page posts the assertion to `POST /oauth/authorize/passkey`. The server:
   - verifies the assertion;
   - loads the account and makes sure it has its DID and handle;
   - checks the account matches the `login_hint`, if there was one;
   - approves the pushed request.
   The server answers with the app's redirect URI, carrying `code`, `state` and `iss`.
4. The page sends the browser to that URI. The app exchanges the code at `/oauth/token` as it
   already does.

A failure keeps the person on the page with a message they can act on: they cancelled the passkey
sheet, the passkey is not registered to a LogDate account, the passkey belongs to a different account
from the one the app asked for, or the request expired and they need to start again in the app. A
mismatch or a failed assertion leaves the pushed request usable, so the person can try another
passkey.

### Where passkeys work

A browser only offers a passkey on a page whose host is the passkey's relying-party ID or a
subdomain of it. The authorization page lives at the OAuth issuer. So the feature only works when
the issuer's host is inside `WEBAUTHN_RP_ID`. For example, `cloud.logdate.app` with the relying
party `logdate.app` works.

When the feature is on:

- the issuer's origin is added to the origins passkey verification accepts, so nobody has to list
  it by hand;
- an issuer outside the relying party stops the server at startup with a message that names both
  values, rather than serving a page whose button can never work.

### Feature flag

`LOGDATE_OAUTH_PASSKEY_SIGN_IN_ENABLED`, off by default. Off, the server behaves as it did before: no
page is served, `POST /oauth/authorize` still needs a bearer token, and the two passkey endpoints
answer `501`.

Turn it on per deployment once two things are true:

- the issuer and relying party line up there. Staging today does not: its issuer is
  `cloud-staging.logdate.app` and its relying party is `staging.logdate.app`;
- the account owner is comfortable with what an approved app can reach. Until journal records move
  out of the public repository, that includes them.

## What this does not change

- The LogDate apps keep their own passkey sign-in and bearer tokens.
- Tokens issued through this page are the same DPoP-bound OAuth tokens as before. They act for the
  account's DID on the AT Protocol methods that accept OAuth tokens: the repository reads and writes
  and blob uploads. They do not reach the LogDate `/api/v1` surface or personal spaces.
- Accounts with an AT Protocol password can still use `createSession`.

## How it is tested

- Route tests cover:
  - the HTML page and its security headers;
  - the flag being off;
  - a `client_id` mismatch and an expired request;
  - the passkey options;
  - approval with a verified assertion;
  - a `login_hint` mismatch, which leaves the request usable;
  - cancelling;
  - the `iss` parameter on every redirect.
- An end-to-end test in `integration/server-client-e2e` starts the real server and a separate
  third-party app that publishes its own client metadata document. It creates a passkey-only account
  with a synthetic authenticator and confirms the account has no password. The app pushes an
  authorization request, the test plays the browser through the page and the passkey ceremony, and
  the app exchanges the code with PKCE and DPoP. The app then uploads a file to the account's
  repository with the access token it received.
