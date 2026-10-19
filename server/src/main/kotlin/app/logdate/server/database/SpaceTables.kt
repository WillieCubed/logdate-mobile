package app.logdate.server.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp

/** The last sequence number issued for one author's repository in one space; its row is the write lock. */
object SpaceStatesTable : Table("space_states") {
    val spaceUri = text("space_uri")
    val authorDid = text("author_did")
    val accountId = javaUUID("account_id").references(AccountsTable.id, onDelete = ReferenceOption.CASCADE)
    val lastSeq = long("last_seq").default(0L)
    override val primaryKey = PrimaryKey(spaceUri, authorDid)
}

/** Sealed records and their tombstones. [record] holds the DAG-CBOR bytes of a live record. */
object SpaceRecordsTable : Table("space_records") {
    val spaceUri = text("space_uri")
    val authorDid = text("author_did")
    val rkey = varchar("rkey", 512)
    val accountId = javaUUID("account_id").references(AccountsTable.id, onDelete = ReferenceOption.CASCADE)
    val seq = long("seq")
    val cid = varchar("cid", 255).nullable()
    val record = binary("record").nullable()
    val sizeBytes = integer("size_bytes").default(0)
    val deleted = bool("deleted").default(false)
    val deletedAt = timestamp("deleted_at").nullable()
    override val primaryKey = PrimaryKey(spaceUri, authorDid, rkey)

    init {
        index("idx_space_records_seq", false, spaceUri, authorDid, seq)
    }
}

/** The blobs each live record refers to, so purging can keep referenced blobs. */
object SpaceRecordBlobsTable : Table("space_record_blobs") {
    val spaceUri = text("space_uri")
    val authorDid = text("author_did")
    val rkey = varchar("rkey", 512)
    val cid = varchar("cid", 255)
    override val primaryKey = PrimaryKey(spaceUri, authorDid, rkey, cid)

    init {
        foreignKey(
            spaceUri to SpaceRecordsTable.spaceUri,
            authorDid to SpaceRecordsTable.authorDid,
            rkey to SpaceRecordsTable.rkey,
            onDelete = ReferenceOption.CASCADE,
        )
    }
}

/** Metadata for encrypted blobs uploaded to a space; the bytes live in object storage at [storagePath]. */
object SpaceBlobsTable : Table("space_blobs") {
    val spaceUri = text("space_uri")
    val cid = varchar("cid", 255)
    val accountId = javaUUID("account_id").references(AccountsTable.id, onDelete = ReferenceOption.CASCADE)
    val mimeType = varchar("mime_type", 255)
    val sizeBytes = long("size_bytes")
    val storagePath = text("storage_path")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(spaceUri, cid)
}
