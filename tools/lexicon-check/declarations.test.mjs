// Tests for the permission-set and space-declaration rules in declarations.mjs. Run with `node --test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  authShareProblems,
  namespaceGroup,
  permissionSetProblems,
  spaceDeclarationProblems,
} from "./declarations.mjs";

const space = (overrides = {}) => ({
  type: "space",
  key: "literal:self",
  name: "Your journal",
  collections: ["app.logdate.crypto.sealed"],
  ...overrides,
});

test("a complete space declaration passes", () => {
  assert.deepEqual(spaceDeclarationProblems(space({ "name:lang": { es: "Tu diario" }, description: "For developers." })), []);
  assert.deepEqual(spaceDeclarationProblems(space({ key: "any", collections: [] })), []);
});

test("a space declaration needs a name, a key rule and its collections", () => {
  assert.deepEqual(spaceDeclarationProblems({ type: "space" }), [
    "#main: a space declaration needs a name",
    "#main: a space declaration needs a key rule",
    "#main: a space declaration needs a collections list",
  ]);
});

test("a space declaration's fields must be well formed", () => {
  assert.deepEqual(
    spaceDeclarationProblems(
      space({
        name: "x".repeat(65),
        "name:lang": { "not a tag": "x" },
        key: "random",
        collections: ["*", "not-an-nsid", "app.logdate.crypto.sealed", "app.logdate.crypto.sealed"],
      }),
    ),
    [
      "#main: the name is longer than 64 characters",
      '#main: name:lang key "not a tag" isn\'t a language tag',
      '#main: "random" isn\'t a key rule',
      "#main: collections can't include the * wildcard",
      '#main: collection "not-an-nsid" isn\'t a valid NSID',
      "#main: collection app.logdate.crypto.sealed is listed twice",
    ],
  );
});

test("a permission set's namespace is its id without the last segment", () => {
  assert.equal(namespaceGroup("app.logdate.authFull"), "app.logdate");
  assert.equal(namespaceGroup("app.bsky.feed.authPosts"), "app.bsky.feed");
});

const set = (...permissions) => ({ type: "permission-set", title: "Example", permissions });

test("a permission set granting its own namespace passes", () => {
  assert.deepEqual(
    permissionSetProblems(
      "app.logdate.authFull",
      set(
        { type: "permission", resource: "repo", collection: ["app.logdate.actor.profile"], action: ["create", "update"] },
        { type: "permission", resource: "rpc", lxm: ["app.logdate.space.getRecord"], inheritAud: true },
        { type: "permission", resource: "rpc", lxm: ["app.logdate.space.getRecord"], aud: "*" },
        {
          type: "permission",
          resource: "space",
          spaceType: "app.logdate.space.circle",
          authority: "*",
          skey: "3jzfcijpj2z2a",
          collection: ["*", "community.lexicon.calendar.event"],
          action: ["read_self", "create"],
          manage: ["create", "update", "delete"],
        },
        { type: "permission", resource: "space", spaceType: "app.logdate.space.personal", authority: "did:plc:abc123" },
      ),
    ),
    [],
  );
});

test("a permission set needs a permissions list of permission entries", () => {
  assert.deepEqual(permissionSetProblems("app.logdate.authFull", { type: "permission-set" }), [
    "#main: a permission set needs a permissions list",
  ]);
  assert.deepEqual(permissionSetProblems("app.logdate.authFull", set({ resource: "repo", collection: ["app.logdate.x.y"] })), [
    '#main.permissions[0]: every entry needs "type": "permission"',
  ]);
});

test("a permission set can't reach outside its namespace or use wildcards", () => {
  assert.deepEqual(
    permissionSetProblems(
      "app.logdate.authFull",
      set(
        { type: "permission", resource: "repo", collection: ["app.bsky.feed.post", "*"] },
        { type: "permission", resource: "rpc", lxm: ["app.logdat.x.y"], inheritAud: true },
        { type: "permission", resource: "space", spaceType: "*" },
        { type: "permission", resource: "space", spaceType: "com.example.forum" },
      ),
    ),
    [
      "#main.permissions[0]: collection app.bsky.feed.post is outside this set's namespace, app.logdate.*",
      "#main.permissions[0]: collection can't be * in a permission set",
      "#main.permissions[1]: lxm app.logdat.x.y is outside this set's namespace, app.logdate.*",
      "#main.permissions[2]: a permission set must name a space type, not *",
      "#main.permissions[3]: spaceType com.example.forum is outside this set's namespace, app.logdate.*",
    ],
  );
});

test("blob, account and identity permissions and unknown resources are refused", () => {
  assert.deepEqual(
    permissionSetProblems(
      "app.logdate.authFull",
      set(
        { type: "permission", resource: "blob", accept: ["*/*"] },
        { type: "permission", resource: "account", attr: "email" },
        { type: "permission", resource: "identity", attr: "handle" },
        { type: "permission", resource: "repos", collection: ["app.logdate.x.y"] },
      ),
    ),
    [
      "#main.permissions[0]: blob permissions can't be in a permission set; apps ask for them directly",
      "#main.permissions[1]: account permissions can't be in a permission set; apps ask for them directly",
      "#main.permissions[2]: identity permissions can't be in a permission set; apps ask for them directly",
      '#main.permissions[3]: "repos" isn\'t a resource a permission set can grant',
    ],
  );
});

test("permission parameters must hold allowed values", () => {
  assert.deepEqual(
    permissionSetProblems(
      "app.logdate.authFull",
      set(
        { type: "permission", resource: "repo", collection: [], action: ["read", "create", "create"] },
        { type: "permission", resource: "rpc", lxm: ["app.logdate.a.b"] },
        { type: "permission", resource: "rpc", lxm: ["app.logdate.a.b"], inheritAud: true, aud: "*" },
        { type: "permission", resource: "rpc", lxm: ["app.logdate.a.b"], aud: "did:web:logdate.app#logdate" },
        {
          type: "permission",
          resource: "space",
          spaceType: "app.logdate.space.group",
          authority: "alice.example",
          skey: "",
          collection: ["nope"],
          action: ["write"],
          manage: ["read"],
        },
      ),
    ),
    [
      "#main.permissions[0]: collection can't be an empty list",
      '#main.permissions[0]: action "read" isn\'t one of create, update, delete',
      "#main.permissions[0]: action create is listed twice",
      "#main.permissions[1]: an rpc permission needs aud: *, or inheritAud: true",
      "#main.permissions[2]: an rpc permission can't set aud and inheritAud together",
      '#main.permissions[3]: aud "did:web:logdate.app#logdate" can\'t name a service in a permission set; use inheritAud: true',
      '#main.permissions[4]: authority "alice.example" isn\'t self, * or a DID',
      '#main.permissions[4]: skey "" isn\'t * or a valid space key',
      '#main.permissions[4]: collection "nope" isn\'t * or a valid NSID',
      '#main.permissions[4]: action "write" isn\'t one of read_self, read, create, update, delete',
      '#main.permissions[4]: manage "read" isn\'t one of create, update, delete',
    ],
  );
});

test("localized titles and details must be keyed by language", () => {
  assert.deepEqual(
    permissionSetProblems("app.logdate.authFull", { ...set(), "title:lang": { fr: "Exemple" }, "detail:lang": ["x"] }),
    ["#main: detail:lang must be an object keyed by language"],
  );
});

test("parameters a resource doesn't take are refused, since servers would drop the entry", () => {
  assert.deepEqual(
    permissionSetProblems(
      "app.logdate.authFull",
      set(
        { type: "permission", resource: "repo", collections: ["app.logdate.actor.profile"] },
        { type: "permission", resource: "rpc", lxm: ["app.logdate.a.b"], inheritAud: true, audience: "*" },
        { type: "permission", resource: "space", spaceType: "app.logdate.space.circle", actions: ["read"], skey: "*" },
      ),
    ),
    [
      '#main.permissions[0]: "collections" isn\'t a parameter of repo permissions',
      "#main.permissions[0]: a repo permission needs at least one collection",
      '#main.permissions[1]: "audience" isn\'t a parameter of rpc permissions',
      '#main.permissions[2]: "actions" isn\'t a parameter of space permissions',
    ],
  );
});

test("lists and single values must have the right shape", () => {
  assert.deepEqual(
    permissionSetProblems(
      "app.logdate.authFull",
      set(
        { type: "permission", resource: "repo", collection: "app.logdate.actor.profile", action: [] },
        { type: "permission", resource: "rpc", lxm: ["app.logdate.a.b", 3], aud: ["*"] },
        { type: "permission", resource: "rpc", lxm: ["app.logdate.a.b"], inheritAud: "true" },
        {
          type: "permission",
          resource: "space",
          spaceType: ["app.logdate.space.circle"],
          authority: ["*"],
          skey: 7,
          collection: "*",
          action: "read",
          manage: [],
        },
      ),
    ),
    [
      "#main.permissions[0]: collection must be a list, even for one value",
      "#main.permissions[0]: action can't be an empty list",
      "#main.permissions[1]: lxm must list only strings",
      "#main.permissions[1]: aud must be a single value, not a list",
      "#main.permissions[2]: inheritAud must be true or false",
      "#main.permissions[2]: an rpc permission needs aud: *, or inheritAud: true",
      "#main.permissions[3]: spaceType must be a single value, not a list",
      "#main.permissions[3]: authority must be a single value, not a list",
      "#main.permissions[3]: skey must be a string",
      "#main.permissions[3]: collection must be a list, even for one value",
      "#main.permissions[3]: action must be a list, even for one value",
      "#main.permissions[3]: manage can't be an empty list",
    ],
  );
});

const sealedOnly = { collections: ["app.logdate.crypto.sealed"] };
const sharedSpaces = (circle = sealedOnly, group = sealedOnly) =>
  new Map([
    ["app.logdate.space.circle", circle],
    ["app.logdate.space.group", group],
  ]);

test("authShare as written stays away from the journal", () => {
  const authShare = set(
    { type: "permission", resource: "space", spaceType: "app.logdate.space.circle", authority: "*" },
    { type: "permission", resource: "space", spaceType: "app.logdate.space.circle", manage: ["create", "update"] },
    { type: "permission", resource: "space", spaceType: "app.logdate.space.group", authority: "*" },
  );
  assert.deepEqual(authShareProblems(authShare, sharedSpaces()), []);
});

test("authShare can't name the personal space, repo permissions, journal methods or group management", () => {
  const authShare = set(
    { type: "permission", resource: "space", spaceType: "app.logdate.space.personal", action: ["read_self"] },
    { type: "permission", resource: "repo", collection: ["app.logdate.crypto.keyDeclaration"] },
    { type: "permission", resource: "rpc", lxm: ["app.logdate.space.getRecord"], inheritAud: true },
    { type: "permission", resource: "space", spaceType: "app.logdate.space.group", authority: "self", manage: ["create"] },
    { type: "permission", resource: "space", spaceType: "app.logdate.space.circle", collection: ["*"] },
  );
  const at = (index) => `app.logdate.authShare#main.permissions[${index}]`;
  assert.deepEqual(authShareProblems(authShare, sharedSpaces()), [
    { where: at(0), message: "authShare can't grant the personal space, which holds the journal" },
    { where: at(1), message: "authShare can't grant repo permissions; they reach the public profile and sharing keys" },
    { where: at(2), message: "authShare can't grant the personal-space methods; they read the journal" },
    { where: at(3), message: "authShare can't manage groups; running a group stays in LogDate's own apps" },
    { where: at(4), message: "collection * widens app.logdate.authShare" },
  ]);
});

test("a collection added to the circle or group declaration widens authShare", () => {
  const widened = { collections: ["app.logdate.crypto.sealed", "app.logdate.journal.entry"] };
  const authShare = set({ type: "permission", resource: "space", spaceType: "app.logdate.space.group", authority: "*" });
  const message =
    "collection app.logdate.journal.entry widens app.logdate.authShare; review the set, then add it to " +
    "authShareReviewedCollections in tools/lexicon-check/declarations.mjs";
  assert.deepEqual(authShareProblems(authShare, sharedSpaces(widened, widened)), [
    { where: "app.logdate.space.circle", message },
    { where: "app.logdate.space.group", message },
  ]);
});
