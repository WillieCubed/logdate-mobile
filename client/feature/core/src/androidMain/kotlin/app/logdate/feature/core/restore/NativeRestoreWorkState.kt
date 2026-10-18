package app.logdate.feature.core.restore

import android.content.Context
import androidx.work.Data
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.restore.PostRestoreDetector

/** Binds queued recovery to the delivery and authenticated owner that requested it. */
class NativeRestoreWorkState(
    context: Context,
    private val input: Data,
    private val sessions: SessionStorage,
) {
    enum class Validation {
        CURRENT,
        STALE,
        WAITING_FOR_SESSION,
    }

    private val detector = PostRestoreDetector(context)
    private val deliveryId = input.getString(DELIVERY_KEY)

    suspend fun validation(): Validation {
        if (deliveryId != null && !detector.isContentRecoveryCurrent(deliveryId)) return Validation.STALE
        val owner = input.getString(OWNER_KEY)
        val origin = input.getString(ORIGIN_KEY)
        if (owner == null && origin == null) return Validation.CURRENT
        if (!sessions.hasValidSession()) return Validation.WAITING_FOR_SESSION
        val current = sessions.getOriginBoundSession() ?: return Validation.WAITING_FOR_SESSION
        return if (current.session.accountId == owner && current.origin == origin) Validation.CURRENT else Validation.STALE
    }

    suspend fun isCurrent(): Boolean = validation() == Validation.CURRENT

    suspend fun complete(expectedSession: OriginBoundSession? = sessions.getOriginBoundSession()): Boolean {
        if (deliveryId == null || !detector.isContentRecoveryCurrent(deliveryId)) return true
        val owner = input.getString(OWNER_KEY)
        val origin = input.getString(ORIGIN_KEY)
        if ((owner != null || origin != null) &&
            (expectedSession?.session?.accountId != owner || expectedSession?.origin != origin)
        ) {
            return false
        }
        var acknowledged = false
        val sessionCurrent =
            sessions.runIfSessionCurrent(expectedSession) {
                acknowledged = detector.acknowledgeContentRecovery(deliveryId)
            }
        return sessionCurrent || !detector.isContentRecoveryCurrent(deliveryId) || acknowledged
    }

    companion object {
        const val DELIVERY_KEY = "native_restore_delivery"
        const val OWNER_KEY = "restore_expected_owner"
        const val ORIGIN_KEY = "restore_expected_origin"

        fun bind(
            builder: Data.Builder,
            deliveryId: String?,
            session: OriginBoundSession?,
        ): Data.Builder {
            if (deliveryId != null) builder.putString(DELIVERY_KEY, deliveryId)
            if (session != null) {
                builder.putString(OWNER_KEY, session.session.accountId)
                builder.putString(ORIGIN_KEY, session.origin)
            }
            return builder
        }
    }
}
