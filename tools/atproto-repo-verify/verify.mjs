#!/usr/bin/env node

// Checks a repository CAR export with the AT Protocol reference implementation (@atproto/repo),
// the same code relays and AppViews run when they consume a repository.
//
// Usage: node verify.mjs <car-file> <account-did> <did:key of the signing key> [previous-rev]
//
// On success it prints a JSON summary of the verified commit and exits 0. On any failure it
// prints the reason to stderr and exits 1.
import { readFile } from "node:fs/promises";
import {
  def,
  MemoryBlockstore,
  MST,
  readCarWithRoot,
  verifyRepo,
} from "@atproto/repo";
import { ensureValidNsid, ensureValidRecordKey, ensureValidTid } from "@atproto/syntax";

const COMMIT_FIELDS = ["data", "did", "prev", "rev", "sig", "version"];

const [carPath, did, didKey, previousRev] = process.argv.slice(2);
if (!carPath || !did || !didKey) {
  console.error("usage: node verify.mjs <car-file> <account-did> <did:key> [previous-rev]");
  process.exit(2);
}

const fail = (message) => {
  throw new Error(message);
};

async function verify() {
  // Reading the CAR checks that every block hashes to its CID.
  const car = await readCarWithRoot(await readFile(carPath));
  const storage = new MemoryBlockstore(car.blocks);

  // The strict version 3 commit shape: `prev` must be present even when it is null.
  const commit = await storage.readObj(car.root, def.commit);
  const fields = Object.keys(commit).sort();
  if (fields.join(",") !== COMMIT_FIELDS.join(",")) {
    fail(`commit has fields [${fields}], expected exactly [${COMMIT_FIELDS}]`);
  }
  ensureValidTid(commit.rev);
  if (previousRev !== undefined && !(commit.rev > previousRev)) {
    fail(`commit rev ${commit.rev} does not come after the previous rev ${previousRev}`);
  }

  // Signature over the unsigned commit, DID match, MST walk and presence of every record block.
  const verified = await verifyRepo(car.blocks, car.root, did, didKey);

  // The MST shape must be the canonical one for its contents: rebuilding the tree from the same
  // entries with the reference implementation has to land on the same root CID.
  let rebuilt = await MST.create(new MemoryBlockstore());
  for (const create of verified.creates) {
    ensureValidNsid(create.collection);
    ensureValidRecordKey(create.rkey);
    rebuilt = await rebuilt.add(`${create.collection}/${create.rkey}`, create.cid);
  }
  const rebuiltRoot = await rebuilt.getPointer();
  if (!rebuiltRoot.equals(commit.data)) {
    fail(`MST root ${commit.data} is not canonical; the reference tree for the same entries is ${rebuiltRoot}`);
  }

  return {
    commit: car.root.toString(),
    did: commit.did,
    rev: commit.rev,
    data: commit.data.toString(),
    prev: commit.prev?.toString() ?? null,
    records: verified.creates.length,
  };
}

try {
  console.log(JSON.stringify(await verify()));
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
