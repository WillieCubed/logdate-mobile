package app.logdate.core.notifications.service

import app.logdate.core.coroutines.AppDispatcher
import app.logdate.core.coroutines.Dispatcher
import app.logdate.core.di.ApplicationScope
import app.logdate.core.network.DeviceRegistrationClient
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.messaging.ktx.messaging
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * A service that provides a token for Firebase Cloud Messaging.
 *
 * The token is used to identify the device to the server. The registration token may change when:
 * - The app is restored on a new device
 * - The user uninstalls/reinstall the app
 * - The user clears app data.
 *
 * Whenever the token changes, the new token is sent to the current LogDate server.
 */
@AndroidEntryPoint
class FirebaseNotificationProvider @Inject constructor(
    private val logdateClient: DeviceRegistrationClient,
    @Dispatcher(AppDispatcher.IO) private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val coroutineScope: CoroutineScope,
) : FirebaseMessagingService(),
    RegistrationTokenProvider,
    RemoteNotificationProvider {

    private val firebaseMessaging: FirebaseMessaging by lazy { Firebase.messaging }

    /**
     * Called when a new token for the device is generated.
     *
     * This implementation sends the new token to the server.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        sendRegistrationToServer(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // TODO: Handle messages when app is in foreground
    }

    /**
     * Performs a full sync with the app server.
     */
    override fun onDeletedMessages() {
        super.onDeletedMessages()
    }

    /**
     * Fetches the current registration token for this device for Firebase Cloud Messaging.
     */
    override suspend fun getToken(): String = FirebaseMessaging.getInstance().token.toCoroutine()

    /**
     * Subscribes to the given Firebase Cloud Messaging topic.
     */
    override suspend fun subscribe(topic: String) {
        firebaseMessaging.subscribeToTopic(topic).await()
    }

    /**
     * Unsubscribes from the given Firebase Cloud Messaging topic.
     */
    override suspend fun unsubscribe(topic: String) {
        firebaseMessaging.unsubscribeFromTopic(topic).await()
    }

    /**
     * Toggles automatic initialization for Firebase Cloud Messaging.
     */
    override fun toggleAutoInit(enabled: Boolean) {
        Firebase.messaging.isAutoInitEnabled = enabled
    }

    private fun sendRegistrationToServer(token: String) {
        coroutineScope.launch(dispatcher) {
            logdateClient.registerDevice(token)
        }
    }
}

