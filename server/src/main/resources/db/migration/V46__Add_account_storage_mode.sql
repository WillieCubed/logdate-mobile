-- How the server keeps each account's journal data: 'legacy' (one REST endpoint per kind of data) or
-- 'personalSpaceV1' (sealed records in the account's personal space). Every account that exists now
-- stays 'legacy'; only accounts created while personal spaces are turned on start in 'personalSpaceV1'.
ALTER TABLE accounts ADD COLUMN storage_mode VARCHAR(32) NOT NULL DEFAULT 'legacy';
