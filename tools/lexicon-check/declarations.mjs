// Checks the two kinds of lexicon that describe access instead of data:
//
// - permission sets (`"type": "permission-set"`), from https://atproto.com/specs/permission;
// - space type declarations (`"type": "space"`), from the AT Protocol Spaces proposal
//   (bluesky-social/proposals, 0016-permissioned-data). Spaces is an alpha and still changing.
//
// The stable reference implementation only checks a permission set's outline and doesn't know
// spaces yet, so these rules follow the specifications directly. The Kotlin checker in
// shared/atproto-lexicon (LexiconValidator) applies the same rules.
import { isValidDid, isValidLanguage, isValidNsid, isValidRecordKey } from "@atproto/syntax";

const recordActions = ["create", "update", "delete"];
const spaceActions = ["read_self", "read", "create", "update", "delete"];
const keyRules = new Set(["any", "tid", "nsid"]);
const maxSpaceNameLength = 64;

/** Returns one message per problem with a space type declaration, the `main` definition `def`. */
export function spaceDeclarationProblems(def) {
  const problems = [];
  const report = (message) => problems.push(`#main: ${message}`);

  if (typeof def.name !== "string" || def.name.length === 0) report("a space declaration needs a name");
  else if (def.name.length > maxSpaceNameLength) report(`the name is longer than ${maxSpaceNameLength} characters`);
  languageMapProblems(def["name:lang"], "name:lang", report);

  if (def.key === undefined) report("a space declaration needs a key rule");
  else if (!isKeyRule(def.key)) report(`"${def.key}" isn't a key rule`);

  if (!Array.isArray(def.collections)) {
    report("a space declaration needs a collections list");
  } else {
    for (const collection of def.collections) {
      if (collection === "*") report("collections can't include the * wildcard");
      else if (!isValidNsid(collection)) report(`collection "${collection}" isn't a valid NSID`);
    }
    duplicates(def.collections).forEach((value) => report(`collection ${value} is listed twice`));
  }
  return problems;
}

/**
 * Returns one message per problem with the permission set `def`, the `main` definition of the
 * document `id`.
 */
export function permissionSetProblems(id, def) {
  const problems = [];
  const report = (message) => problems.push(`#main: ${message}`);
  languageMapProblems(def["title:lang"], "title:lang", report);
  languageMapProblems(def["detail:lang"], "detail:lang", report);

  if (!Array.isArray(def.permissions)) {
    report("a permission set needs a permissions list");
    return problems;
  }
  const group = namespaceGroup(id);
  def.permissions.forEach((permission, index) => {
    for (const problem of permissionProblems(permission, group)) problems.push(`#main.permissions[${index}]: ${problem}`);
  });
  return problems;
}

/**
 * Returns one message per problem with one entry of a permission set whose namespace is `group`.
 * Authorization servers silently ignore an entry they can't read, such as one with a misspelled
 * parameter or a single value where a list belongs, so every such mistake is a problem here.
 */
export function permissionProblems(permission, group) {
  const problems = [];
  const report = (message) => problems.push(message);
  if (permission?.type !== "permission") {
    report('every entry needs "type": "permission"');
    return problems;
  }
  const check = resourceChecks[permission.resource];
  if (!check) {
    report(`"${permission.resource}" isn't a resource a permission set can grant`);
    return problems;
  }
  const shape = parameterShapes[permission.resource];
  const wellFormed = shape ? shapeProblems(permission, shape, report) : new Set();
  check(permission, group, report, wellFormed);
  return problems;
}

/** The NSID group a permission set may grant within: its own id without the last segment. */
export function namespaceGroup(id) {
  return id.split(".").slice(0, -1).join(".");
}

const withinGroup = (nsid, group) => nsid.startsWith(`${group}.`);

// The parameters each resource takes, and whether each is a list, a single string or a boolean.
const parameterShapes = {
  repo: { collection: "list", action: "list" },
  rpc: { lxm: "list", aud: "string", inheritAud: "boolean" },
  space: { spaceType: "string", authority: "string", skey: "string", collection: "list", action: "list", manage: "list" },
};

// Reports unknown parameters and parameters of the wrong shape, and returns the names of the
// parameters that are present and well formed.
function shapeProblems(permission, shape, report) {
  const wellFormed = new Set();
  for (const [name, value] of Object.entries(permission)) {
    if (name === "type" || name === "resource") continue;
    const kind = shape[name];
    if (!kind) {
      report(`"${name}" isn't a parameter of ${permission.resource} permissions`);
      continue;
    }
    const problem = shapeProblem(name, kind, value);
    if (problem) report(problem);
    else wellFormed.add(name);
  }
  return wellFormed;
}

function shapeProblem(name, kind, value) {
  switch (kind) {
    case "list":
      if (!Array.isArray(value)) return `${name} must be a list, even for one value`;
      if (value.length === 0) return `${name} can't be an empty list`;
      if (!value.every((entry) => typeof entry === "string")) return `${name} must list only strings`;
      return null;
    case "string":
      if (Array.isArray(value)) return `${name} must be a single value, not a list`;
      return typeof value === "string" ? null : `${name} must be a string`;
    case "boolean":
      return typeof value === "boolean" ? null : `${name} must be true or false`;
    default:
      return null;
  }
}

const resourceChecks = {
  repo(permission, group, report, wellFormed) {
    if (permission.collection === undefined) report("a repo permission needs at least one collection");
    if (wellFormed.has("collection")) permission.collection.forEach((collection) => ownNsid(collection, "collection", group, report));
    if (wellFormed.has("action")) allowedValues(permission.action, recordActions, "action", report);
  },
  rpc(permission, group, report, wellFormed) {
    if (permission.lxm === undefined) report("an rpc permission needs at least one lxm");
    if (wellFormed.has("lxm")) permission.lxm.forEach((method) => ownNsid(method, "lxm", group, report));
    const inherits = permission.inheritAud === true;
    if (inherits && permission.aud !== undefined) report("an rpc permission can't set aud and inheritAud together");
    if (!inherits && permission.aud === undefined) report("an rpc permission needs aud: *, or inheritAud: true");
    // A set can't name a service: the audience comes from the app's include scope instead.
    if (wellFormed.has("aud") && permission.aud !== "*") {
      report(`aud "${permission.aud}" can't name a service in a permission set; use inheritAud: true`);
    }
  },
  space(permission, group, report, wellFormed) {
    const spaceType = permission.spaceType;
    if (spaceType === undefined) report("a space permission needs a spaceType");
    else if (spaceType === "*") report("a permission set must name a space type, not *");
    else if (wellFormed.has("spaceType")) ownNsid(spaceType, "spaceType", group, report);

    const authority = permission.authority;
    if (wellFormed.has("authority") && authority !== "self" && authority !== "*" && !isValidDid(authority)) {
      report(`authority "${authority}" isn't self, * or a DID`);
    }
    const skey = permission.skey;
    if (wellFormed.has("skey") && skey !== "*" && !isValidRecordKey(skey)) {
      report(`skey "${skey}" isn't * or a valid space key`);
    }
    if (wellFormed.has("collection")) {
      for (const collection of permission.collection) {
        if (collection !== "*" && !isValidNsid(collection)) report(`collection "${collection}" isn't * or a valid NSID`);
      }
    }
    if (wellFormed.has("action")) allowedValues(permission.action, spaceActions, "action", report);
    if (wellFormed.has("manage")) allowedValues(permission.manage, recordActions, "manage", report);
  },
  blob: (_permission, _group, report) => report("blob permissions can't be in a permission set; apps ask for them directly"),
  account: (_permission, _group, report) => report("account permissions can't be in a permission set; apps ask for them directly"),
  identity: (_permission, _group, report) => report("identity permissions can't be in a permission set; apps ask for them directly"),
};

function ownNsid(nsid, parameter, group, report) {
  if (nsid === "*") report(`${parameter} can't be * in a permission set`);
  else if (typeof nsid !== "string" || !isValidNsid(nsid)) report(`${parameter} "${nsid}" isn't a valid NSID`);
  else if (!withinGroup(nsid, group)) report(`${parameter} ${nsid} is outside this set's namespace, ${group}.*`);
}

function allowedValues(value, allowed, parameter, report) {
  if (value === undefined) return;
  const list = values(value);
  for (const entry of list) {
    if (!allowed.includes(entry)) report(`${parameter} "${entry}" isn't one of ${allowed.join(", ")}`);
  }
  duplicates(list).forEach((entry) => report(`${parameter} ${entry} is listed twice`));
}

function languageMapProblems(map, field, report) {
  if (map === undefined) return;
  if (!map || typeof map !== "object" || Array.isArray(map)) return report(`${field} must be an object keyed by language`);
  for (const [language, text] of Object.entries(map)) {
    if (!isValidLanguage(language)) report(`${field} key "${language}" isn't a language tag`);
    if (typeof text !== "string") report(`${field}.${language} must be a string`);
  }
}

const isKeyRule = (key) =>
  typeof key === "string" && (keyRules.has(key) || (key.startsWith("literal:") && isValidRecordKey(key.slice(8))));

/** A parameter's values: scope parameters may be one value or a list. */
export const values = (value) => (value === undefined ? [] : Array.isArray(value) ? value : [value]);

const duplicates = (list) => [...new Set(list.filter((entry, index) => list.indexOf(entry) !== index))];

export const authShareId = "app.logdate.authShare";
const personalSpace = "app.logdate.space.personal";
const groupSpace = "app.logdate.space.group";
const sharedSpaces = ["app.logdate.space.circle", groupSpace];
const personalSpaceMethods = "app.logdate.space.";

/**
 * The collections of the circle and group space declarations that app.logdate.authShare was
 * reviewed against. authShare grants a declaration's collections by default, so a collection
 * added to either declaration also reaches every app holding authShare. Add one here only after
 * reviewing what that gives a third-party sharing app.
 */
export const authShareReviewedCollections = ["app.logdate.crypto.sealed"];

/**
 * Keeps app.logdate.authShare away from the journal and from running groups. `set` is its `main`
 * definition and `spaces` maps each space type to its declaration's `main` definition. Returns
 * `{ where, message }` for each problem.
 */
export function authShareProblems(set, spaces) {
  const problems = [];
  const report = (where, message) => problems.push({ where, message });
  (set.permissions ?? []).forEach((permission, index) => {
    const at = `${authShareId}#main.permissions[${index}]`;
    if (permission?.resource === "repo") {
      report(at, "authShare can't grant repo permissions; they reach the public profile and sharing keys");
    }
    if (permission?.resource === "rpc" && values(permission.lxm).some((method) => String(method).startsWith(personalSpaceMethods))) {
      report(at, "authShare can't grant the personal-space methods; they read the journal");
    }
    if (permission?.resource !== "space") return;
    if (permission.spaceType === personalSpace) report(at, "authShare can't grant the personal space, which holds the journal");
    if (permission.spaceType === groupSpace && permission.manage !== undefined) {
      report(at, "authShare can't manage groups; running a group stays in LogDate's own apps");
    }
    for (const collection of values(permission.collection)) {
      if (!authShareReviewedCollections.includes(collection)) report(at, `collection ${collection} widens ${authShareId}`);
    }
  });
  for (const spaceType of sharedSpaces) {
    for (const collection of spaces.get(spaceType)?.collections ?? []) {
      if (!authShareReviewedCollections.includes(collection)) {
        report(
          spaceType,
          `collection ${collection} widens ${authShareId}; review the set, then add it to authShareReviewedCollections ` +
            "in tools/lexicon-check/declarations.mjs",
        );
      }
    }
  }
  return problems;
}
