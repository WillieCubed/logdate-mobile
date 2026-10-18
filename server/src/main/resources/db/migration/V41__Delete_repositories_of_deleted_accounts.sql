-- Deleting an account used to leave its journal repository and sync index behind, because
-- neither had a foreign key to accounts. This removes what deleted accounts left, and makes the
-- sync index follow its account. Repository rows are keyed by DID, so the server deletes them with
-- the account (deleteAccountRepositories); there is no account column for a foreign key.

DELETE FROM logdate_collection_records r
WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = r.user_id);

DELETE FROM logdate_collection_states s
WHERE NOT EXISTS (SELECT 1 FROM accounts a WHERE a.id = s.user_id);

ALTER TABLE logdate_collection_records
    ADD CONSTRAINT fk_logdate_collection_records_account
    FOREIGN KEY (user_id) REFERENCES accounts(id) ON DELETE CASCADE;

ALTER TABLE logdate_collection_states
    ADD CONSTRAINT fk_logdate_collection_states_account
    FOREIGN KEY (user_id) REFERENCES accounts(id) ON DELETE CASCADE;

-- A repository belongs to an account through the account's DID, the repository DID its sync state
-- recorded, or the synthetic DID sync uses for an account without one (syntheticRepoDid). Anything
-- else was left by a deleted account.
CREATE TEMPORARY TABLE live_repo_dids ON COMMIT DROP AS
    SELECT did AS repo_did FROM accounts WHERE did IS NOT NULL
    UNION
    SELECT 'did:web:sync-' || replace(id::text, '-', '') || '.logdate.internal' FROM accounts
    UNION
    SELECT repo_did FROM logdate_collection_states;

DELETE FROM atproto_repo_heads h
WHERE NOT EXISTS (SELECT 1 FROM live_repo_dids l WHERE l.repo_did = h.repo_did);

DELETE FROM atproto_repo_commits c
WHERE NOT EXISTS (SELECT 1 FROM live_repo_dids l WHERE l.repo_did = c.repo_did);

DELETE FROM atproto_repo_block_links k
WHERE NOT EXISTS (SELECT 1 FROM live_repo_dids l WHERE l.repo_did = k.repo_did);

-- Every block is written together with a link, so a block no repository links to is unreachable.
DELETE FROM atproto_repo_blocks b
WHERE NOT EXISTS (SELECT 1 FROM atproto_repo_block_links k WHERE k.cid = b.cid);
