-- Additive: existing blobs and clients are unchanged. Receipts disappear only with their blob.
CREATE TABLE space_blob_upload_receipts (
    space_uri TEXT NOT NULL,
    upload_intent VARCHAR(36) NOT NULL,
    cid VARCHAR(255) NOT NULL,
    PRIMARY KEY (space_uri, upload_intent),
    FOREIGN KEY (space_uri, cid) REFERENCES space_blobs(space_uri, cid) ON DELETE CASCADE
);
