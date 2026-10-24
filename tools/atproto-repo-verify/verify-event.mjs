#!/usr/bin/env node

// Checks the CAR file of a repository event stream commit (a `#commit` event's `blocks`) the way a
// relay does, with the AT Protocol reference implementation (@atproto/repo).
//
// Usage: node verify-event.mjs <previous-repo-car|-> <event-car> <ops-json-file> <account-did> <did:key> <prev-data|->
//
// <previous-repo-car> is the full export of the repository before the commit, or "-" for a first
// commit. <ops-json-file> holds the event's `ops` as [{action, path, cid, prev}]. <prev-data> is the
// event's `prevData` CID, or "-" when the event has none.
//
// It verifies that the commit is signed by the account's key and names the account, that the blocks
// are enough to read every change the commit made, that those changes are exactly the event's ops,
// and that undoing the ops on the new tree gives back the previous tree (the "inductive" check). On
// success it prints a JSON summary and exits 0. On any failure it prints the reason to stderr and
// exits 1.
import { readFile } from "node:fs/promises";
import {
  def,
  MemoryBlockstore,
  MST,
  Repo,
  readCarWithRoot,
  verifyCommitSig,
  verifyDiffCar,
} from "@atproto/repo";

const [previousCarPath, eventCarPath, opsPath, did, didKey, prevData] = process.argv.slice(2);
if (!previousCarPath || !eventCarPath || !opsPath || !did || !didKey || !prevData) {
  console.error("usage: node verify-event.mjs <previous-repo-car|-> <event-car> <ops-json> <did> <did:key> <prev-data|->");
  process.exit(2);
}

const fail = (message) => {
  throw new Error(message);
};

async function verify() {
  const ops = JSON.parse(await readFile(opsPath, "utf8"));
  const eventCarBytes = await readFile(eventCarPath);

  let previousRepo = null;
  if (previousCarPath !== "-") {
    const previous = await readCarWithRoot(await readFile(previousCarPath));
    previousRepo = await Repo.load(new MemoryBlockstore(previous.blocks), previous.root);
  }

  // Signature, DID, and that the blocks reach every changed node of the tree.
  const diff = await verifyDiffCar(previousRepo, eventCarBytes, did, didKey);

  const event = await readCarWithRoot(eventCarBytes);
  const storage = new MemoryBlockstore(event.blocks);
  const commit = await storage.readObj(event.root, def.commit);
  if (commit.did !== did) fail(`commit names ${commit.did}, not ${did}`);
  if (!(await verifyCommitSig(commit, didKey))) fail("commit signature does not verify");

  // The changes the reference implementation reads must be exactly the ones the event announces.
  const seen = diff.writes
    .map((write) => ({
      action: write.action,
      path: `${write.collection}/${write.rkey}`,
      cid: write.action === "delete" ? null : write.cid.toString(),
    }))
    .sort((a, b) => a.path.localeCompare(b.path));
  const claimed = ops
    .map((op) => ({ action: op.action, path: op.path, cid: op.cid ?? null }))
    .sort((a, b) => a.path.localeCompare(b.path));
  if (JSON.stringify(seen) !== JSON.stringify(claimed)) {
    fail(`ops do not match the blocks: blocks say ${JSON.stringify(seen)}, event says ${JSON.stringify(claimed)}`);
  }

  // Undo the ops on the new tree. A relay does this to learn the tree the commit started from, and
  // checks it against `prevData`.
  let tree = await MST.load(storage, commit.data);
  for (const op of ops) {
    if (op.action === "create") {
      tree = await tree.delete(op.path);
      continue;
    }
    // The CID an update or delete replaced is in the previous tree, which is also where it is checked.
    const replaced = previousRepo ? await previousRepo.data.get(op.path) : null;
    if (!replaced || replaced.toString() !== op.prev) {
      fail(`${op.path} had ${replaced?.toString() ?? "no record"} before the commit, but the event says ${op.prev}`);
    }
    if (op.action === "update") {
      tree = await tree.update(op.path, replaced);
    } else if (op.action === "delete") {
      tree = await tree.add(op.path, replaced);
    } else {
      fail(`unknown op action ${op.action}`);
    }
  }
  const inverted = (await tree.getPointer()).toString();
  const expected = prevData === "-" ? (await (await MST.create(new MemoryBlockstore())).getPointer()).toString() : prevData;
  if (inverted !== expected) fail(`undoing the ops gives tree ${inverted}, but prevData is ${expected}`);

  return { commit: event.root.toString(), rev: commit.rev, ops: ops.length, blocks: event.blocks.size };
}

try {
  console.log(JSON.stringify(await verify()));
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
