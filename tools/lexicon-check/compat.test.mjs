// Tests for the published-schema compatibility rules in compat.mjs. Run with `node --test`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { breakingChanges } from "./compat.mjs";

const record = (properties, required = [], extra = {}) => ({
  lexicon: 1,
  id: "app.logdate.example.note",
  defs: {
    main: {
      type: "record",
      key: "tid",
      record: { type: "object", required, properties },
      ...extra,
    },
  },
});

const base = record(
  {
    text: { type: "string", maxLength: 100 },
    count: { type: "integer", minimum: 0, maximum: 10 },
    kind: { type: "string", knownValues: ["a", "b"] },
    mode: { type: "string", enum: ["x", "y"] },
    media: { type: "blob", accept: ["image/*"], maxSize: 1000 },
    blocks: { type: "array", items: { type: "union", refs: ["#one", "#two"] }, maxLength: 5 },
    choice: { type: "union", refs: ["#one"], closed: true },
    link: { type: "ref", ref: "#one" },
  },
  ["text"],
);
base.defs.one = { type: "object", properties: { name: { type: "string" } } };
base.defs.two = { type: "object", properties: {} };

const clone = (value) => structuredClone(value);
const edit = (change) => {
  const next = clone(base);
  change(next.defs.main.record.properties, next);
  return breakingChanges(base, next);
};

test("an unchanged schema has no breaking changes", () => {
  assert.deepEqual(breakingChanges(base, clone(base)), []);
});

test("compatible changes pass", () => {
  assert.deepEqual(
    edit((p, doc) => {
      p.extra = { type: "string" };
      p.kind.knownValues.push("c");
      p.blocks.items.refs.push("#three");
      doc.defs.three = { type: "object", properties: {} };
      p.text.maxLength = 200;
      p.count.maximum = 20;
      p.count.minimum = -5;
      p.media.accept = ["image/*", "video/*"];
      p.media.maxSize = 2000;
      p.blocks.maxLength = 10;
      p.text.description = "Better words.";
    }),
    [],
  );
});

test("removing or renaming a field breaks", () => {
  assert.deepEqual(
    edit((p) => {
      p.body = p.text;
      delete p.text;
    }),
    ["#main.record.text: the field was removed"],
  );
});

test("making an optional field required breaks", () => {
  assert.deepEqual(
    edit((p, doc) => doc.defs.main.record.required.push("count")),
    ["#main.record.count: an optional field became required"],
  );
});

test("adding a new required field breaks, since existing records lack it", () => {
  assert.deepEqual(
    edit((p, doc) => {
      p.title = { type: "string" };
      doc.defs.main.record.required.push("title");
    }),
    ["#main.record.title: a new field is required"],
  );
});

test("changing a field's type breaks", () => {
  assert.deepEqual(edit((p) => (p.count = { type: "string" })), ["#main.record.count: the type changed from integer to string"]);
});

test("tightening a limit breaks", () => {
  assert.deepEqual(
    edit((p) => {
      p.text.maxLength = 50;
      p.count.minimum = 1;
      p.media.maxSize = 500;
      p.blocks.maxLength = 3;
    }),
    [
      "#main.record.text: maxLength tightened from 100 to 50",
      "#main.record.count: minimum tightened from 0 to 1",
      "#main.record.media: maxSize tightened from 1000 to 500",
      "#main.record.blocks: maxLength tightened from 5 to 3",
    ],
  );
});

test("adding a limit where there was none breaks", () => {
  assert.deepEqual(edit((p) => (p.kind.maxLength = 10)), ["#main.record.kind: maxLength was added"]);
});

test("narrowing a blob's accepted types breaks", () => {
  assert.deepEqual(edit((p) => (p.media.accept = ["image/png"])), ["#main.record.media: accept no longer allows image/*"]);
});

test("removing a known value is allowed but changing a closed enum breaks", () => {
  assert.deepEqual(edit((p) => (p.kind.knownValues = ["a"])), []);
  assert.deepEqual(edit((p) => p.mode.enum.push("z")), ["#main.record.mode: the closed enum changed"]);
});

test("removing a union member or closing an open union breaks", () => {
  assert.deepEqual(edit((p) => (p.blocks.items.refs = ["#one"])), ["#main.record.blocks[]: union member #two was removed"]);
  assert.deepEqual(edit((p) => (p.blocks.items.closed = true)), ["#main.record.blocks[]: an open union became closed"]);
});

test("adding a member to a closed union breaks", () => {
  assert.deepEqual(edit((p) => p.choice.refs.push("#two")), ["#main.record.choice: closed union gained member #two"]);
});

test("pointing a reference elsewhere breaks", () => {
  assert.deepEqual(edit((p) => (p.link.ref = "#two")), ["#main.record.link: the reference changed from #one to #two"]);
});

test("changes inside a referenced definition are found", () => {
  assert.deepEqual(
    edit((p, doc) => delete doc.defs.one.properties.name),
    ["#one.name: the field was removed"],
  );
});

test("removing a definition or changing the record key breaks", () => {
  assert.deepEqual(
    edit((p, doc) => delete doc.defs.two),
    ["#two: the definition was removed"],
  );
  assert.deepEqual(
    edit((p, doc) => (doc.defs.main.key = "any")),
    ["#main: the record key changed from tid to any"],
  );
});

test("a nullable field that stops being nullable breaks", () => {
  const nullableBase = clone(base);
  nullableBase.defs.main.record.nullable = ["text"];
  assert.deepEqual(breakingChanges(nullableBase, base), ["#main.record.text: the field is no longer nullable"]);
});

const permissionSet = {
  lexicon: 1,
  id: "app.logdate.authExample",
  defs: {
    main: {
      type: "permission-set",
      title: "Example",
      detail: "Example access.",
      permissions: [
        { type: "permission", resource: "repo", collection: ["app.logdate.actor.profile", "app.logdate.share.item"] },
        { type: "permission", resource: "rpc", lxm: ["app.logdate.space.getRecord"], inheritAud: true },
        {
          type: "permission",
          resource: "space",
          spaceType: "app.logdate.space.circle",
          authority: "*",
          action: ["read", "create"],
          manage: ["create"],
        },
      ],
    },
  },
};
const editSet = (change) => {
  const next = clone(permissionSet);
  change(next.defs.main.permissions, next.defs.main);
  return breakingChanges(permissionSet, next);
};
const circles = "app.logdate.space.circle spaces (authority *, skey *)";

test("a permission set may gain permissions and change its wording", () => {
  assert.deepEqual(
    editSet((permissions, main) => {
      main.title = "Better example";
      main.detail = "Clearer words.";
      permissions[0].collection.push("app.logdate.share.reply");
      permissions[2].action.push("update");
      permissions[2].manage.push("delete");
      permissions.push({ type: "permission", resource: "repo", collection: ["app.logdate.graph.mute"] });
    }),
    [],
  );
});

test("a permission set may regroup the same grants differently", () => {
  assert.deepEqual(
    editSet((permissions) => {
      permissions[0] = { type: "permission", resource: "repo", collection: ["app.logdate.actor.profile"] };
      permissions.push({
        type: "permission",
        resource: "repo",
        collection: ["app.logdate.share.item"],
        action: ["create", "update", "delete"],
      });
      permissions[2].action = ["read", "create", "update", "delete"];
    }),
    [],
  );
});

test("removing a permission from a published set breaks", () => {
  assert.deepEqual(
    editSet((permissions) => permissions.splice(1, 1)),
    ["#main.permissions: the set no longer grants rpc app.logdate.space.getRecord for the app's audience"],
  );
});

test("narrowing a repo permission breaks", () => {
  assert.deepEqual(
    editSet((permissions) => {
      permissions[0].collection = ["app.logdate.actor.profile"];
      permissions.push({ type: "permission", resource: "repo", collection: ["app.logdate.share.item"], action: ["create"] });
    }),
    [
      "#main.permissions: the set no longer grants repo update on app.logdate.share.item",
      "#main.permissions: the set no longer grants repo delete on app.logdate.share.item",
    ],
  );
});

test("narrowing a space permission breaks", () => {
  const narrowed = [
    `#main.permissions: the set no longer grants read in ${circles}`,
    `#main.permissions: the set no longer grants create of the space type's declared collections in ${circles}`,
    `#main.permissions: the set no longer grants manage=create of ${circles}`,
  ];
  assert.deepEqual(
    editSet((permissions) => {
      permissions[2].action = ["read_self"];
      delete permissions[2].manage;
    }),
    narrowed,
  );
  assert.deepEqual(
    editSet((permissions) => delete permissions[2].authority),
    narrowed,
  );
});

test("listing a space permission's collections where it used the declared ones breaks, and * covers them", () => {
  assert.deepEqual(
    editSet((permissions) => (permissions[2].collection = ["app.logdate.crypto.sealed"])),
    [`#main.permissions: the set no longer grants create of the space type's declared collections in ${circles}`],
  );
  assert.deepEqual(
    editSet((permissions) => (permissions[2].collection = ["*"])),
    [],
  );
});

test("a malformed permission grants nothing, so a typo that drops a grant breaks", () => {
  const lostRepo = ["app.logdate.actor.profile", "app.logdate.share.item"].flatMap((collection) =>
    ["create", "update", "delete"].map((action) => `#main.permissions: the set no longer grants repo ${action} on ${collection}`),
  );
  assert.deepEqual(
    editSet((permissions) => {
      permissions[0].collections = permissions[0].collection;
      delete permissions[0].collection;
    }),
    lostRepo,
  );
  assert.deepEqual(
    editSet((permissions) => (permissions[0].action = "create")),
    lostRepo,
  );
  assert.deepEqual(
    editSet((permissions) => (permissions[2].authority = ["*"])),
    [
      `#main.permissions: the set no longer grants read in ${circles}`,
      `#main.permissions: the set no longer grants create of the space type's declared collections in ${circles}`,
      `#main.permissions: the set no longer grants manage=create of ${circles}`,
    ],
  );
});

test("fixing a malformed permission isn't breaking", () => {
  const malformed = clone(permissionSet);
  malformed.defs.main.permissions[2].authority = ["*"];
  assert.deepEqual(breakingChanges(malformed, permissionSet), []);
});

test("read covers read_self", () => {
  const readSelf = clone(permissionSet);
  readSelf.defs.main.permissions[2].action = ["read_self", "create"];
  assert.deepEqual(breakingChanges(readSelf, permissionSet), []);
  assert.deepEqual(breakingChanges(permissionSet, readSelf), [`#main.permissions: the set no longer grants read in ${circles}`]);
});

const space = {
  lexicon: 1,
  id: "app.logdate.space.circle",
  defs: {
    main: {
      type: "space",
      key: "any",
      name: "Shared with people",
      collections: ["app.logdate.crypto.sealed", "app.logdate.crypto.wrappedKey"],
    },
  },
};
const editSpace = (change) => {
  const next = clone(space);
  change(next.defs.main);
  return breakingChanges(space, next);
};

test("a space declaration may gain collections and change its names", () => {
  assert.deepEqual(
    editSpace((main) => {
      main.collections.push("app.logdate.share.reaction");
      main.name = "Things you share";
      main["name:lang"] = { es: "Lo que compartes" };
      main.description = "Better words.";
    }),
    [],
  );
});

test("narrowing a space's collections or changing its key breaks", () => {
  assert.deepEqual(
    editSpace((main) => (main.collections = ["app.logdate.crypto.sealed"])),
    ["#main.collections: app.logdate.crypto.wrappedKey was removed"],
  );
  assert.deepEqual(
    editSpace((main) => (main.key = "tid")),
    ["#main: the space key changed from any to tid"],
  );
});

test("a space declaration can't become another kind of definition", () => {
  assert.deepEqual(
    editSpace((main) => (main.type = "token")),
    ["#main: the type changed from space to token"],
  );
});
