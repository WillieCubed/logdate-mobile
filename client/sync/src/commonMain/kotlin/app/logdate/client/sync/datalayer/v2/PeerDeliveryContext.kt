package app.logdate.client.sync.datalayer.v2

import app.logdate.client.sync.peer.link.LinkWire
import kotlinx.serialization.Serializable

/** The verified link that owns a delivery; source nodes and account provenance travel together. */
@Serializable
data class PeerDeliveryContext(
    val linkId: String,
    val phoneNode: String,
    val watchNode: String,
    val mode: LinkMode,
    val accountId: String? = null,
    val serverOrigin: String? = null,
) {
    val ledgerScope: String get() = "link:$linkId"

    /** A canonical URL-safe spelling of the public exchange identity, safe for a path component. */
    val linkPathSegment: String
        get() {
            require(isValid()) { "A delivery path requires a valid link context" }
            return linkId.replace('+', '-').replace('/', '_').removeSuffix("=")
        }

    fun isValid(): Boolean {
        val hash = LinkWire.decodeKey(linkId) ?: return false
        if (hash.size != 32 || LinkWire.encodeKey(hash) != linkId) return false
        if (phoneNode.isBlank() || watchNode.isBlank() || phoneNode == watchNode) return false
        return when (mode) {
            LinkMode.PAIR -> accountId == null && serverOrigin == null
            LinkMode.CLOUD -> !accountId.isNullOrBlank() && !serverOrigin.isNullOrBlank()
        }
    }
}
