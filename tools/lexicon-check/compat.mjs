// Finds changes between two versions of a lexicon schema that would break records or apps that
// already use it, following "Changing a schema" in docs/reference/lexicon-style.md. Allowed
// changes (new optional fields, new known values, new members of an open union, looser limits,
// better descriptions) pass; everything that could reject or misread an existing record doesn't.
//
// Permission sets and space declarations are read again by authorization servers while apps'
// sessions are live, so a change to one changes what apps that already asked for it can do. Adding
// access is allowed; taking any away is breaking.
import { values } from "./declarations.mjs";

/**
 * Returns one message per breaking change from `before` to `after`, each prefixed with the
 * definition path it applies to, such as `#main.record.text: the field was removed`.
 */
export function breakingChanges(before, after) {
  const problems = [];
  for (const [name, oldDef] of Object.entries(before.defs ?? {})) {
    const newDef = after.defs?.[name];
    if (!newDef) problems.push(`#${name}: the definition was removed`);
    else compareDefinition(`#${name}`, oldDef, newDef, problems);
  }
  return problems;
}

function compareDefinition(path, before, after, problems) {
  if (before.type !== after.type) {
    problems.push(`${path}: the type changed from ${before.type} to ${after.type}`);
    return;
  }
  switch (before.type) {
    case "record":
      if (before.key !== after.key) problems.push(`${path}: the record key changed from ${before.key} to ${after.key}`);
      compareField(`${path}.record`, before.record, after.record, problems);
      return;
    case "query":
    case "procedure":
    case "subscription":
      for (const part of ["parameters", "message"]) {
        if (before[part] && after[part]) compareField(`${path}.${part}`, before[part], after[part], problems);
      }
      for (const part of ["input", "output"]) {
        if (before[part]?.schema && after[part]?.schema) {
          compareField(`${path}.${part}`, before[part].schema, after[part].schema, problems);
        }
      }
      return;
    case "permission-set":
      comparePermissionSet(path, before, after, problems);
      return;
    case "space":
      compareSpace(path, before, after, problems);
      return;
    case "token":
      return;
    default:
      compareField(path, before, after, problems);
  }
}

function compareField(path, before, after, problems) {
  if (before.type !== after.type) {
    problems.push(`${path}: the type changed from ${before.type} to ${after.type}`);
    return;
  }
  const report = (message) => problems.push(`${path}: ${message}`);
  switch (before.type) {
    case "object":
    case "params":
      compareObject(path, before, after, problems);
      break;
    case "array":
      tightenedMaximum("maxLength", before, after, report);
      tightenedMinimum("minLength", before, after, report);
      compareField(`${path}[]`, before.items, after.items, problems);
      break;
    case "string":
      ["maxLength", "maxGraphemes"].forEach((limit) => tightenedMaximum(limit, before, after, report));
      ["minLength", "minGraphemes"].forEach((limit) => tightenedMinimum(limit, before, after, report));
      if (before.format !== after.format) report(`the format changed from ${before.format ?? "none"} to ${after.format ?? "none"}`);
      compareClosedValues(before, after, report);
      break;
    case "integer":
      tightenedMaximum("maximum", before, after, report);
      tightenedMinimum("minimum", before, after, report);
      compareClosedValues(before, after, report);
      break;
    case "boolean":
      if (before.const !== after.const) report("the constant value changed");
      break;
    case "bytes":
      tightenedMaximum("maxLength", before, after, report);
      tightenedMinimum("minLength", before, after, report);
      break;
    case "blob":
      tightenedMaximum("maxSize", before, after, report);
      compareAccept(before.accept, after.accept, report);
      break;
    case "union":
      compareUnion(before, after, report);
      break;
    case "ref":
      if (before.ref !== after.ref) report(`the reference changed from ${before.ref} to ${after.ref}`);
      break;
    default:
      break;
  }
}

function compareObject(path, before, after, problems) {
  const oldProperties = before.properties ?? {};
  const newProperties = after.properties ?? {};
  const oldRequired = new Set(before.required ?? []);
  const newNullable = new Set(after.nullable ?? []);

  for (const [name, oldField] of Object.entries(oldProperties)) {
    const newField = newProperties[name];
    if (!newField) problems.push(`${path}.${name}: the field was removed`);
    else compareField(`${path}.${name}`, oldField, newField, problems);
  }
  for (const name of after.required ?? []) {
    if (!(name in oldProperties)) problems.push(`${path}.${name}: a new field is required`);
    else if (!oldRequired.has(name)) problems.push(`${path}.${name}: an optional field became required`);
  }
  for (const name of before.nullable ?? []) {
    if (name in newProperties && !newNullable.has(name)) problems.push(`${path}.${name}: the field is no longer nullable`);
  }
}

function compareUnion(before, after, report) {
  const newRefs = new Set(after.refs ?? []);
  const oldRefs = new Set(before.refs ?? []);
  if (!before.closed && after.closed) report("an open union became closed");
  for (const ref of oldRefs) if (!newRefs.has(ref)) report(`union member ${ref} was removed`);
  if (before.closed) for (const ref of newRefs) if (!oldRefs.has(ref)) report(`closed union gained member ${ref}`);
}

// A closed `enum` or a `const` fixes the allowed values, so any change can reject or misread an
// existing record. `knownValues` is only a hint, and may change freely.
function compareClosedValues(before, after, report) {
  if (JSON.stringify(before.enum) !== JSON.stringify(after.enum)) report("the closed enum changed");
  if (before.const !== after.const) report("the constant value changed");
}

function compareAccept(before, after, report) {
  if (!before) {
    if (after) report("accept was added");
    return;
  }
  const accepted = after ?? ["*/*"];
  for (const type of before) {
    if (!accepted.some((pattern) => mimeCovers(pattern, type))) report(`accept no longer allows ${type}`);
  }
}

function mimeCovers(pattern, type) {
  if (pattern === "*/*" || pattern === type) return true;
  if (!pattern.endsWith("/*")) return false;
  const family = pattern.slice(0, -1);
  return type.startsWith(family) && !type.endsWith("/*");
}

function tightenedMaximum(limit, before, after, report) {
  if (after[limit] === undefined) return;
  if (before[limit] === undefined) report(`${limit} was added`);
  else if (after[limit] < before[limit]) report(`${limit} tightened from ${before[limit]} to ${after[limit]}`);
}

function tightenedMinimum(limit, before, after, report) {
  if (after[limit] === undefined) return;
  if (before[limit] === undefined) report(`${limit} was added`);
  else if (after[limit] > before[limit]) report(`${limit} tightened from ${before[limit]} to ${after[limit]}`);
}

// A space declaration's collections are the default write access of every `space:` grant for
// its type, and its key rule describes the keys existing spaces already have.
function compareSpace(path, before, after, problems) {
  if (before.key !== after.key) problems.push(`${path}: the space key changed from ${before.key} to ${after.key}`);
  const kept = new Set(after.collections ?? []);
  for (const collection of before.collections ?? []) {
    if (!kept.has(collection)) problems.push(`${path}.collections: ${collection} was removed`);
  }
}

// Every grant the old set made must still be made by some permission in the new one.
function comparePermissionSet(path, before, after, problems) {
  const newPermissions = after.permissions ?? [];
  for (const permission of before.permissions ?? []) {
    for (const grant of grants(permission)) {
      if (!newPermissions.some((candidate) => covers(candidate, grant))) {
        problems.push(`${path}.permissions: the set no longer grants ${grant.label}`);
      }
    }
  }
}

const allRecordActions = ["create", "update", "delete"];
const allSpaceActions = ["read", "create", "update", "delete"];
const declaredCollections = "the space type's declared collections";

// Splits a permission into the smallest grants it makes, so a narrowing anywhere shows up as a
// missing grant.
function grants(permission) {
  switch (permission.resource) {
    case "repo": {
      const actions = permission.action === undefined ? allRecordActions : values(permission.action);
      return values(permission.collection).flatMap((collection) =>
        actions.map((action) => ({ resource: "repo", collection, action, label: `repo ${action} on ${collection}` })),
      );
    }
    case "rpc": {
      const audience = permission.inheritAud === true ? "the app's audience" : permission.aud;
      return values(permission.lxm).map((lxm) => ({
        resource: "rpc",
        lxm,
        inheritAud: permission.inheritAud === true,
        aud: permission.aud,
        label: `rpc ${lxm} for ${audience}`,
      }));
    }
    case "space": {
      const where = spaceSelector(permission);
      const name = `${where.spaceType} spaces (authority ${where.authority}, skey ${where.skey})`;
      const actions = permission.action === undefined ? allSpaceActions : values(permission.action);
      const collections = permission.collection === undefined ? [declaredCollections] : values(permission.collection);
      const records = actions.flatMap((action) =>
        action === "read" || action === "read_self"
          ? [{ ...where, kind: "action", action, label: `${action} in ${name}` }]
          : collections.map((collection) => ({
              ...where,
              kind: "action",
              action,
              collection,
              label: `${action} of ${collection} in ${name}`,
            })),
      );
      const management = values(permission.manage).map((verb) => ({ ...where, kind: "manage", verb, label: `manage=${verb} of ${name}` }));
      return [...records, ...management];
    }
    default:
      return [{ resource: permission.resource, permission, label: `the ${permission.resource} permission ${JSON.stringify(permission)}` }];
  }
}

function spaceSelector(permission) {
  return {
    resource: "space",
    spaceType: permission.spaceType,
    authority: permission.authority ?? "self",
    skey: permission.skey ?? "*",
  };
}

function covers(permission, grant) {
  if (permission.resource !== grant.resource) return false;
  switch (grant.resource) {
    case "repo": {
      const actions = permission.action === undefined ? allRecordActions : values(permission.action);
      return values(permission.collection).includes(grant.collection) && actions.includes(grant.action);
    }
    case "rpc":
      return (
        values(permission.lxm).includes(grant.lxm) &&
        (permission.inheritAud === true) === grant.inheritAud &&
        (grant.inheritAud || permission.aud === grant.aud || permission.aud === "*")
      );
    case "space": {
      const where = spaceSelector(permission);
      if (where.spaceType !== grant.spaceType) return false;
      if (where.authority !== "*" && where.authority !== grant.authority) return false;
      if (where.skey !== "*" && where.skey !== grant.skey) return false;
      if (grant.kind === "manage") return values(permission.manage).includes(grant.verb);
      const actions = permission.action === undefined ? allSpaceActions : values(permission.action);
      if (grant.action === "read_self") return actions.includes("read_self") || actions.includes("read");
      if (!actions.includes(grant.action)) return false;
      if (grant.collection === undefined) return true;
      const collections = permission.collection === undefined ? [declaredCollections] : values(permission.collection);
      return collections.includes("*") || collections.includes(grant.collection);
    }
    default:
      return JSON.stringify(permission) === JSON.stringify(grant.permission);
  }
}
