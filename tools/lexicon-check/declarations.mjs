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
    const at = (message) => problems.push(`#main.permissions[${index}]: ${message}`);
    if (permission?.type !== "permission") return at('every entry needs "type": "permission"');
    const check = resourceChecks[permission.resource];
    if (!check) return at(`"${permission.resource}" isn't a resource a permission set can grant`);
    check(permission, group, at);
  });
  return problems;
}

/** The NSID group a permission set may grant within: its own id without the last segment. */
export function namespaceGroup(id) {
  return id.split(".").slice(0, -1).join(".");
}

const withinGroup = (nsid, group) => nsid.startsWith(`${group}.`);

const resourceChecks = {
  repo(permission, group, report) {
    const collections = values(permission.collection);
    if (collections.length === 0) report("a repo permission needs at least one collection");
    collections.forEach((collection) => ownNsid(collection, "collection", group, report));
    allowedValues(permission.action, recordActions, "action", report);
  },
  rpc(permission, group, report) {
    const methods = values(permission.lxm);
    if (methods.length === 0) report("an rpc permission needs at least one lxm");
    methods.forEach((method) => ownNsid(method, "lxm", group, report));
    const inherits = permission.inheritAud === true;
    if (permission.inheritAud !== undefined && typeof permission.inheritAud !== "boolean") {
      report("inheritAud must be true or false");
    }
    if (inherits && permission.aud !== undefined) report("an rpc permission can't set aud and inheritAud together");
    if (!inherits && permission.aud === undefined) report("an rpc permission needs aud: *, or inheritAud: true");
    // A set can't name a service: the audience comes from the app's include scope instead.
    if (permission.aud !== undefined && permission.aud !== "*") {
      report(`aud "${permission.aud}" can't name a service in a permission set; use inheritAud: true`);
    }
  },
  space(permission, group, report) {
    const spaceType = permission.spaceType;
    if (typeof spaceType !== "string") report("a space permission needs a spaceType");
    else if (spaceType === "*") report("a permission set must name a space type, not *");
    else ownNsid(spaceType, "spaceType", group, report);

    const authority = permission.authority;
    if (authority !== undefined && authority !== "self" && authority !== "*" && !isValidDid(authority)) {
      report(`authority "${authority}" isn't self, * or a DID`);
    }
    const skey = permission.skey;
    if (skey !== undefined && skey !== "*" && !(typeof skey === "string" && isValidRecordKey(skey))) {
      report(`skey "${skey}" isn't * or a valid space key`);
    }
    for (const collection of values(permission.collection)) {
      if (collection !== "*" && !isValidNsid(collection)) report(`collection "${collection}" isn't * or a valid NSID`);
    }
    allowedValues(permission.action, spaceActions, "action", report);
    allowedValues(permission.manage, recordActions, "manage", report);
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
