# Handle resolution

Every LogDate account has an AT Protocol handle, such as `alice.logdate.app`. Handle resolution is
how another app, such as Bluesky, turns that handle into the account behind it. This page explains
the protocol concept first, then how LogDate's systems answer, the decisions that shaped that
design, and the limits it has today. The [handle resolution runbook](../runbook/handle-resolution.md)
covers tasks such as checking a handle or rolling out a change, and the
[handle resolution reference](../reference/handle-resolution.md) lists exact responses, naming
rules, commands and settings.

## Handles and DIDs

Suppose someone on Bluesky mentions `alice.logdate.app`. Bluesky can't use that name directly,
because handles are meant to be readable and changeable: Alice could change it tomorrow. What
Bluesky needs is Alice's DID, a permanent identifier such as `did:plc:abcdefghijklmnopqrstuvwx`
that stays the same however the handle changes. Every record and every follow in the AT Protocol
points at a DID, not a handle.

So the question an app asks is "which DID does `alice.logdate.app` belong to?" The protocol leaves
the answer to whoever controls the domain, and lets them publish it in one of two places:

- a DNS TXT record at `_atproto.alice.logdate.app` whose value is `did=<the DID>`, or
- a plain-text file at `https://alice.logdate.app/.well-known/atproto-did` whose whole body is the
  DID.

Finding a DID isn't enough on its own, though, because anyone who controls a domain can claim any
DID. The app therefore checks the claim from the other side. It fetches the DID document, which
only the DID's owner can change, and looks for `at://alice.logdate.app` in its `alsoKnownAs` list.
The app trusts the handle only when both sides agree: the domain points at the DID, and the DID
points back at the domain.

## Resolution through the logdate.app website

For LogDate, the domain side of that check means answering for thousands of names under
`logdate.app`, one per account, and keeping every answer in step with the accounts table as people
sign up and leave. The simplest way to stay in step is to never copy the answer anywhere: whatever
answers the app should ask the server's database each time.

That rules out the DNS option. A TXT record per account would be a second copy of the accounts
table, written on every signup and removed on every deletion, and it would drift whenever a write
failed. It would also need the server to hold a Cloudflare API token, and every change would take
time to spread through DNS. LogDate therefore answers over HTTPS.

The HTTPS option raises a different question: which system receives requests for
`alice.logdate.app`? The logdate.app website already does. Cloudflare sends every `*.logdate.app`
host to the website's Cloudflare Worker, because those hosts are also where people's public pages
live. Sending one path of every such host to the server instead would mean splitting each hostname
between two systems, and Cloud Run can't accept wildcard hostnames without a Google load balancer
or Cloudflare rules that rewrite requests on the way in. Instead, the website keeps the hostname and
forwards just this one question to the server, the same way it already forwards DID documents.

A lookup therefore goes like this:

1. The app requests `https://alice.logdate.app/.well-known/atproto-did`, and Cloudflare's wildcard
   record delivers it to the website.
2. The website checks that `alice` is a name it may answer for, then asks the LogDate server which
   DID owns `alice.logdate.app`.
3. The server looks the handle up in its accounts table and replies, and the website passes that
   reply back to the app as plain text.

Because nothing in this chain stores an answer, a new handle resolves the moment its account has
an identity, and a deleted account stops resolving the moment it's gone. The website is careful
about one case: if it can't reach the server, it returns an error rather than "not found". An app
that hears "not found" may conclude the account doesn't exist, which is the wrong conclusion during
an outage.

The server still keeps its own `/.well-known/atproto-did` route. A self-hosted LogDate server can
point its handle domain straight at itself and answer without a website in front. LogDate's own
deployments don't use that route.

## Reserved names and public handle hosts

Answering on the website's own hosts means every handle is also a web address that the website
serves, and that has two consequences.

The first is that some usernames can never become handles. If someone signed up as `www`, their
handle would be `www.logdate.app`, the website's own address. The username `cloud` would give them
`cloud.logdate.app`, where the server runs, and `pricing` would collide with the pricing page. The
server and the website each keep a list of these reserved names: the server refuses them when an
account is created or renamed, and the website never answers a lookup for one. The server also
refuses names starting with `xn--`, because browsers display these punycode names as non-ASCII text
that could imitate someone else's handle.

The second is that handle hosts must stay open to the public. In staging, the website runs at
`staging.logdate.app` behind Cloudflare Access so that only staff can see it, and an outside app
can't sign in to Cloudflare Access to ask about a handle. Staging handles therefore live under a
separate domain, `cloud-staging.logdate.app`, which is not behind Access.

## Current limitations

Resolving the handle covers only the domain side of the two-way check. The other side, the DID
document, depends on LogDate publishing its `did:plc` identities to the public PLC directory, and
that publishing is still turned off. Until it's on, an app can follow `alice.logdate.app` to Alice's
DID but can't fetch the document that would confirm it, so it shows the handle as unverified.

Some accounts can't be resolved at all yet. Accounts created before LogDate supported the AT
Protocol have no handle or DID. Each gets one the next time its owner signs in, or when an operator
runs the identity backfill; looking up a handle never creates one.

Finally, LogDate controls only its own answers. Other apps cache what they resolve, and Bluesky,
for example, can keep showing a deleted handle for hours after LogDate stops answering for it.
