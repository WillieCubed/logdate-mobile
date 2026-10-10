package app.logdate.client.sync.peer.state

import app.logdate.client.sync.datalayer.v2.Decoded
import kotlinx.coroutines.flow.Flow

/** A state document another node wrote, readable or not. */
data class PeerDocument(
    val nodeId: String,
    val decoded: Decoded<StateDocument>,
)

/** Where this device writes its state document and reads the other devices'. */
interface StateDocumentChannel {
    /** Writes this device's document as an urgent data item; false, with the reason logged, when it could not. */
    suspend fun publish(document: StateDocument): Boolean

    /** The documents other nodes wrote, to catch up on changes made while this process was not running. */
    suspend fun peerDocuments(): List<PeerDocument>
}

/** This device's dynamic Data Layer capabilities. */
interface LocalCapabilities {
    /** True once [capability] is advertised, including when it already was. */
    suspend fun add(capability: String): Boolean

    /** True once [capability] is not advertised, including when it never was. */
    suspend fun remove(capability: String): Boolean
}

/** Whether a reachable node advertises a capability, at once and whenever that changes. */
fun interface PeerCapabilityPresence {
    fun observe(capability: String): Flow<Boolean>
}
