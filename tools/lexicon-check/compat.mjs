// Finds changes between two versions of a lexicon schema that would break records or apps that
// already use it, following "Changing a schema" in docs/reference/lexicon-style.md. Allowed
// changes (new optional fields, new known values, new members of an open union, looser limits,
// better descriptions) pass; everything that could reject or misread an existing record doesn't.

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
    case "token":
    case "permission-set":
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
