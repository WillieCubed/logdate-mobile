package app.logdate.server.identity

import studio.hypertext.atproto.crypto.EcKeySupport
import studio.hypertext.atproto.identity.AtprotoDid
import studio.hypertext.atproto.identity.DidDocument
import studio.hypertext.atproto.plc.PlcDirectoryClient
import studio.hypertext.atproto.plc.PlcDirectoryException
import studio.hypertext.atproto.plc.PlcEncoding
import studio.hypertext.atproto.plc.PlcIndexedOperation
import studio.hypertext.atproto.plc.PlcLogEntry
import studio.hypertext.atproto.plc.PlcOperation
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * An in-memory PLC directory that checks submissions the way the real one does: a genesis must
 * derive the DID it is sent for, an update must name the latest entry as `prev`, and every
 * operation must be signed by a rotation key of the entry it follows (or its own, for a genesis).
 */
@OptIn(ExperimentalEncodingApi::class)
internal class FakePlcDirectory : PlcDirectoryClient {
    private val logs = linkedMapOf<AtprotoDid, MutableList<PlcIndexedOperation>>()
    var submissions: Int = 0
        private set

    /** Status codes the next submissions fail with, in order. */
    val failNextSubmits: ArrayDeque<Int> = ArrayDeque()

    fun log(did: String): List<PlcIndexedOperation> = logs[AtprotoDid.require(did)].orEmpty()

    /** Adds an entry without any checks, for states only someone else could have produced. */
    fun seed(
        did: String,
        operation: PlcOperation,
    ) {
        append(AtprotoDid.require(did), operation)
    }

    override suspend fun getDocument(did: AtprotoDid): Result<DidDocument> = error("unused")

    override suspend fun getOperationLog(did: AtprotoDid): Result<List<PlcLogEntry>> = error("unused")

    override suspend fun getAuditLog(did: AtprotoDid): Result<List<PlcIndexedOperation>> =
        runCatching {
            logs[did]?.toList() ?: throw PlcDirectoryException("PLC audit log fetch failed with 404", statusCode = 404)
        }

    override suspend fun export(
        after: String?,
        count: Int?,
    ): Result<List<PlcIndexedOperation>> = error("unused")

    override suspend fun submit(
        did: AtprotoDid,
        entry: PlcLogEntry,
    ): Result<Unit> =
        runCatching {
            submissions += 1
            failNextSubmits.removeFirstOrNull()?.let { status ->
                throw PlcDirectoryException(
                    "PLC operation submission failed with $status",
                    statusCode = status,
                    directoryMessage = "Injected",
                )
            }
            val operation = entry as PlcOperation
            val existing = logs[did]
            if (existing == null) {
                reject(operation.isGenesis, "Unknown DID and the operation is not a genesis")
                reject(PlcEncoding.deriveDid(operation) == did, "Genesis does not derive $did")
                reject(signedByOneOf(operation, operation.rotationKeys), "Invalid signature")
            } else {
                val latest = existing.last { !it.nullified }
                reject(operation.prev == latest.cid, "Proposed prev does not match the most recent operation")
                reject(signedByOneOf(operation, (latest.operation as PlcOperation).rotationKeys), "Invalid signature")
            }
            append(did, operation)
        }

    private fun append(
        did: AtprotoDid,
        operation: PlcOperation,
    ) {
        val log = logs.getOrPut(did) { mutableListOf() }
        log +=
            PlcIndexedOperation(
                did = did,
                operation = operation,
                cid = "bafyfake${logs.values.sumOf { it.size } + 1}",
                nullified = false,
                createdAt = "2026-10-18T00:00:00Z",
            )
    }

    private fun signedByOneOf(
        operation: PlcOperation,
        rotationKeys: List<String>,
    ): Boolean {
        val payload = PlcEncoding.encodeUnsigned(operation.unsigned())
        val signature = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(requireNotNull(operation.sig))
        return rotationKeys.any { didKey ->
            val decoded = EcKeySupport.decodePublicKey(didKey.removePrefix("did:key:"))
            EcKeySupport.verifySha256(decoded.publicKey, decoded.curve, payload, signature)
        }
    }

    private fun reject(
        condition: Boolean,
        message: String,
    ) {
        if (!condition) {
            throw PlcDirectoryException(
                "PLC operation submission failed with 400",
                statusCode = 400,
                directoryMessage = message,
            )
        }
    }
}
