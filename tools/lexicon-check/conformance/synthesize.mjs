// Builds example records from a lexicon, so the conformance fixtures follow the schemas instead of
// being written by hand beside them. Everything here is deterministic: the same lexicons always
// produce the same records.

const DAG_CBOR_CID = "bafyreidpai6xycffexlsyxnuh653jnsg4ewa2czatzn6wos7nioh6zd2nm";
export const RAW_CID = "bafkreibme22gw2h7y2h7tg2fhqotaqjucnbc24deqo72b6mkl2egezxhvy";
const FIXED_TEXT = "Pancakes at the lake";
const MAX_DEPTH = 3;
const MAX_FAULT_DEPTH = 2;
const MAX_VIOLATION_SIZE = 5000;
const MAX_VIOLATION_ITEMS = 100;
const MAX_UNION_MEMBERS = 6;
const MISSING = Symbol("missing");

const formatSamples = {
  datetime: "2026-10-16T18:30:00.000Z",
  did: "did:plc:ewvi7nxzyoun6zhxrhs64oiz",
  nsid: "app.logdate.journal.entry",
  "record-key": "3jzfcijpj2z2a",
  language: "en",
  cid: DAG_CBOR_CID,
  uri: "https://example.com/story",
  "at-uri": "at://did:plc:ewvi7nxzyoun6zhxrhs64oiz/app.logdate.journal.entry/3jzfcijpj2z2a",
  handle: "alice.example.com",
  "at-identifier": "alice.example.com",
  tid: "3jzfcijpj2z2a",
};

const badFormatSamples = {
  datetime: "yesterday afternoon",
  did: "not-a-did",
  nsid: "not an nsid",
  "record-key": "a/b",
  language: "!!",
  cid: "not a cid",
  uri: "no scheme here",
  "at-uri": "not an at-uri",
  handle: "not a handle",
  "at-identifier": "not an identifier",
  tid: "tid",
};

const textByFieldName = {
  id: "b1",
  latitude: "44.9778",
  longitude: "-93.2650",
  timeZone: "America/Chicago",
  mimeType: "image/jpeg",
  name: "Lake house",
  title: "A day at the lake",
  alt: "Four people around a table on a porch",
  displayName: "Alice",
};

const clamp = (value, minimum, maximum) => Math.min(Math.max(value, minimum ?? -Infinity), maximum ?? Infinity);

/** Resolves lexicon references and builds records from them. */
export class Synthesizer {
  /** @param docs a Map from lexicon id to its parsed document */
  constructor(docs) {
    this.docs = docs;
  }

  resolve(ref, fromId) {
    const [id, name = "main"] = ref.startsWith("#") ? [fromId, ref.slice(1)] : ref.split("#");
    const docId = ref.startsWith("#") ? fromId : id;
    const def = this.docs.get(docId)?.defs[name];
    if (!def) throw new Error(`Can't resolve ${ref} from ${fromId}`);
    return { docId, name, def };
  }

  typeName({ docId, name }) {
    return name === "main" ? docId : `${docId}#${name}`;
  }

  /** The main record schema of a record lexicon, as an object schema. */
  recordSchema(id) {
    return this.docs.get(id).defs.main.record;
  }

  /** A record with only its required fields. */
  minimal(id) {
    return { $type: id, ...this.object(this.recordSchema(id), id, "minimal", 0) };
  }

  /** A record with every field filled in, and every member of a list of union values present. */
  full(id) {
    return { $type: id, ...this.object(this.recordSchema(id), id, "full", 0) };
  }

  object(schema, docId, mode, depth) {
    const result = {};
    const fillOptional = mode === "full" && depth < MAX_DEPTH;
    for (const [name, field] of Object.entries(schema.properties ?? {})) {
      if (!(schema.required ?? []).includes(name) && !fillOptional) continue;
      result[name] = this.value(field, docId, name, mode, depth + 1);
    }
    return result;
  }

  value(field, docId, name, mode, depth) {
    switch (field.type) {
      case "string":
        return this.string(field, name);
      case "integer":
        return this.integer(field);
      case "boolean":
        return field.const ?? true;
      case "bytes":
        return { $bytes: Buffer.from(Array.from({ length: bytesLength(field) }, (_, i) => i + 1)).toString("base64") };
      case "cid-link":
        return { $link: DAG_CBOR_CID };
      case "blob":
        return blob(field);
      case "unknown":
        return { note: FIXED_TEXT };
      case "array":
        return this.array(field, docId, name, mode, depth);
      case "object":
        return this.object(field, docId, mode, depth);
      case "ref":
        return this.reference(field.ref, docId, name, mode, depth);
      case "union":
        return this.member(field.refs[0], docId, mode, depth);
      default:
        throw new Error(`Can't build a value for ${name}: unsupported type ${field.type}`);
    }
  }

  member(ref, docId, mode, depth) {
    const target = this.resolve(ref, docId);
    return { $type: this.typeName(target), ...this.object(target.def, target.docId, mode, depth) };
  }

  reference(ref, docId, name, mode, depth) {
    const target = this.resolve(ref, docId);
    if (target.def.type === "record") return this.object(target.def.record, target.docId, mode, depth);
    return this.value(target.def, target.docId, name, mode, depth);
  }

  array(field, docId, name, mode, depth) {
    if (mode === "full" && field.items.type === "union") {
      const count = Math.min(MAX_UNION_MEMBERS, field.maxLength ?? Infinity);
      return field.items.refs.slice(0, count).map((ref) => this.member(ref, docId, mode, depth));
    }
    const count = Math.min(Math.max(field.minLength ?? 1, field.maxLength === 0 ? 0 : 1), field.maxLength ?? Infinity);
    return Array.from({ length: count }, () => this.value(field.items, docId, name, mode, depth));
  }

  string(field, name) {
    if (field.const !== undefined) return field.const;
    if (field.enum) return field.enum[0];
    if (field.knownValues?.length) return field.knownValues[0];
    if (field.format) return formatSamples[field.format] ?? FIXED_TEXT;
    let text = textByFieldName[name] ?? FIXED_TEXT;
    const longest = Math.min(field.maxLength ?? Infinity, field.maxGraphemes ?? Infinity);
    if (text.length > longest) text = text.slice(0, longest);
    const shortest = Math.max(field.minLength ?? 0, field.minGraphemes ?? 0);
    return text.padEnd(shortest, "x");
  }

  integer(field) {
    if (field.const !== undefined) return field.const;
    if (field.enum) return field.enum[0];
    return clamp(7, field.minimum, field.maximum);
  }

  /**
   * Copies of the minimal record with a single fault each, and the failure a validator should
   * report for it. Every required field gets a missing-field case. For the rest, the first field
   * of each kind of fault, at any depth, is enough.
   */
  invalidCases(id) {
    const cases = [];
    const names = new Set();
    const seen = new Set();
    const once = (key) => !seen.has(key) && seen.add(key);
    let nestedMissing = false;

    const add = (label, path, record, kind) => {
      const base = [label, ...path.slice(label === "wrong-record-type" ? 1 : 0)].join("-");
      let name = base;
      for (let n = 2; names.has(name); n += 1) name = `${base}-${n}`;
      names.add(name);
      cases.push({ name, record, expected: { kind, path: `/${path.join("/")}` } });
    };

    add("wrong-record-type", ["$type"], { ...this.minimal(id), $type: "app.logdate.not.this" }, "wrong-record-type");

    const visit = (schema, docId, ancestors) => {
      const parent = ancestors.flatMap((step) => (step.array ? [step.key, 0] : [step.key]));
      for (const [name, field] of Object.entries(schema.properties ?? {})) {
        const path = [...parent, name];
        const mutated = (value) => this.withValue(id, ancestors, name, value);
        if ((schema.required ?? []).includes(name) && (ancestors.length === 0 || !nestedMissing)) {
          nestedMissing ||= ancestors.length > 0;
          add("missing", path, mutated(MISSING), "missing-required");
        }
        if (field.type !== "union" && once(`wrong-type:${field.type}`)) add("wrong-type", path, mutated(wrongTypeValue(field)), "wrong-type");
        for (const fault of this.constraintFaults(field, docId)) {
          if (once(fault.key)) add(fault.label, [...path, ...(fault.within ?? [])], mutated(fault.value), fault.kind);
        }
        if (ancestors.length >= MAX_FAULT_DEPTH) continue;
        for (const child of this.children(field, docId, name)) visit(child.schema, child.docId, [...ancestors, child.step]);
      }
    };
    visit(this.recordSchema(id), id, []);
    return cases;
  }

  /** The object schemas inside a field, each with the step that leads into it. */
  children(field, docId, key) {
    const step = { key, field, docId, array: false };
    if (field.type === "object") return [{ schema: field, docId, step }];
    if (field.type === "ref") {
      const target = this.resolve(field.ref, docId);
      return target.def.type === "object" ? [{ schema: target.def, docId: target.docId, step }] : [];
    }
    if (field.type === "union") {
      const first = this.resolve(field.refs[0], docId);
      return first.def.type === "object" ? [{ schema: first.def, docId: first.docId, step }] : [];
    }
    if (field.type !== "array") return [];
    return this.children(field.items, docId, key).map((child) => ({ ...child, step: { ...child.step, field, array: true } }));
  }

  /** The minimal record, with `name` inside the objects the steps lead through set to `value` or removed. */
  withValue(id, ancestors, name, value) {
    const record = this.minimal(id);
    let node = record;
    for (const { key, field, docId, array } of ancestors) {
      node[key] ??= this.value(field, docId, key, "minimal", 1);
      node = array ? node[key][0] : node[key];
    }
    if (value === MISSING) delete node[name];
    else node[name] = value;
    return record;
  }

  constraintFaults(field, docId) {
    const faults = [];
    const add = (key, label, kind, value, within) => faults.push({ key, label, kind, value, within });
    if (field.type === "string") {
      if (field.enum) add("not-allowed:string", "not-allowed", "not-allowed-value", "not-a-listed-value");
      else if (field.const !== undefined) add("not-allowed:string", "not-allowed", "not-allowed-value", `${field.const}-changed`);
      const limit = [field.maxGraphemes, field.maxLength].find((n) => n !== undefined && n <= MAX_VIOLATION_SIZE);
      if (limit !== undefined) add("too-long:string", "too-long", "too-long", "a".repeat(limit + 1));
      if ((field.minLength ?? 0) > 0 || (field.minGraphemes ?? 0) > 0) add("too-short:string", "too-short", "too-short", "");
      if (field.format && badFormatSamples[field.format]) {
        add(`invalid-format:${field.format}`, "invalid-format", "invalid-format", badFormatSamples[field.format]);
      }
    }
    if (field.type === "integer") {
      if (field.enum) add("not-allowed:integer", "not-allowed", "not-allowed-value", 999999);
      if (field.minimum !== undefined) add("out-of-range:minimum", "below-minimum", "out-of-range", field.minimum - 1);
      if (field.maximum !== undefined && field.maximum < Number.MAX_SAFE_INTEGER) {
        add("out-of-range:maximum", "above-maximum", "out-of-range", field.maximum + 1);
      }
    }
    if (field.type === "array") {
      if (field.maxLength !== undefined && field.maxLength <= MAX_VIOLATION_ITEMS) {
        const items = Array.from({ length: field.maxLength + 1 }, () => this.value(field.items, docId, "item", "minimal", 1));
        add("too-long:array", "too-long", "too-long", items);
      }
      if ((field.minLength ?? 0) > 0) add("too-short:array", "too-short", "too-short", []);
    }
    const union = field.type === "array" ? field.items : field;
    if (union.type === "union") {
      const wrap = (value) => (field.type === "array" ? [value] : value);
      const within = field.type === "array" ? [0] : undefined;
      if (union.closed) {
        add("union-member-not-allowed", "union-member-not-allowed", "union-member-not-allowed", wrap({ $type: "app.logdate.not.listed" }), within);
      }
      add("union-member-missing-type", "union-member-missing-type", "union-member-missing-type", wrap({}), within);
    }
    return faults;
  }
}

function bytesLength(field) {
  const wanted = Math.max(field.minLength ?? 0, 6);
  return Math.min(wanted, field.maxLength ?? wanted);
}

function blob(field) {
  const accepted = field.accept?.[0] ?? "application/octet-stream";
  const mimeType = accepted === "*/*" ? "application/octet-stream" : accepted.endsWith("/*") ? `${accepted.slice(0, -1)}example` : accepted;
  return { $type: "blob", ref: { $link: RAW_CID }, mimeType, size: Math.min(1024, field.maxSize ?? 1024) };
}

function wrongTypeValue(field) {
  switch (field.type) {
    case "string":
      return 42;
    case "integer":
      return "seven";
    case "boolean":
      return "yes";
    default:
      return "not the right type";
  }
}
