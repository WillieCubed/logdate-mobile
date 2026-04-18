package app.logdate.wear.sync
import app.logdate.client.sync.datalayer.WearAudioRequestPaths
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.PutDataRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleWearDataLayerClientTest {
    private val dataClient = mockk<DataClient>()
    private val channelClient = mockk<ChannelClient>()
    private val capabilityClient = mockk<CapabilityClient>()
    private val nodeClient = mockk<NodeClient>()
    private val messageClient = mockk<MessageClient>()

    @Test
    fun `putDataItem returns true when wearable data client succeeds`() =
        runTest {
            val request = mockk<PutDataRequest>(relaxed = true)
            every { dataClient.putDataItem(any()) } returns Tasks.forResult(mockk<DataItem>(relaxed = true))

            val client = createClient(this, putDataRequestFactory = WearPutDataRequestFactory { _, _ -> request })
            val success = client.putDataItem(path = "/logdate/notes/1", data = mapOf("id" to "1"))

            assertTrue(success)
            verify(exactly = 1) { dataClient.putDataItem(request) }
        }

    fun `deleteDataItem returns false when wearable delete fails`() =
        runTest {
            every { dataClient.deleteDataItems(any()) } returns Tasks.forException(IllegalStateException("boom"))

            val client = createClient(this)

            assertFalse(client.deleteDataItem("/logdate/notes/1"))
        }

    @Test
    fun `isPhoneConnected returns true when reachable phone capability exists`() =
        runTest {
            val node = mockk<Node>()
            val capabilityInfo =
                mockk<CapabilityInfo> {
                    every { nodes } returns setOf(node)
                }
            every {
                capabilityClient.getCapability(
                    WearDataLayerClient.PHONE_CAPABILITY,
                    CapabilityClient.FILTER_REACHABLE,
                )
            } returns Tasks.forResult(capabilityInfo)

            val client = createClient(this)

            assertTrue(client.isPhoneConnected())
        }

    @Test
    fun `isPhoneConnected returns false when capability has no reachable nodes`() =
        runTest {
            val capabilityInfo =
                mockk<CapabilityInfo> {
                    every { nodes } returns emptySet()
                }
            every { capabilityClient.getCapability(any(), any()) } returns Tasks.forResult(capabilityInfo)

            val client = createClient(this)

            assertFalse(client.isPhoneConnected())
        }

    @Test
    fun `isPhoneConnected falls back to connected nodes when capability lookup fails`() =
        runTest {
            val node = mockk<Node>()
            every { capabilityClient.getCapability(any(), any()) } returns Tasks.forException(IllegalStateException("boom"))
            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(node))

            val client = createClient(this)

            assertTrue(client.isPhoneConnected())
        }

    @Test
    fun `isPhoneConnected returns false when capability and node lookups both fail`() =
        runTest {
            every { capabilityClient.getCapability(any(), any()) } returns Tasks.forException(IllegalStateException("boom"))
            every { nodeClient.connectedNodes } returns Tasks.forException(IllegalStateException("boom"))

            val client = createClient(this)

            assertFalse(client.isPhoneConnected())
        }

    @Test
    fun `getConnectedPhoneName returns capability node name when available`() =
        runTest {
            val node =
                mockk<Node> {
                    every { displayName } returns "Pixel Watch Phone"
                }
            val capabilityInfo =
                mockk<CapabilityInfo> {
                    every { nodes } returns setOf(node)
                }
            every { capabilityClient.getCapability(any(), any()) } returns Tasks.forResult(capabilityInfo)

            val client = createClient(this)

            assertEquals("Pixel Watch Phone", client.getConnectedPhoneName())
        }

    @Test
    fun `getConnectedPhoneName returns null when capability lookup finds no nodes`() =
        runTest {
            val capabilityInfo =
                mockk<CapabilityInfo> {
                    every { nodes } returns emptySet()
                }
            every { capabilityClient.getCapability(any(), any()) } returns Tasks.forResult(capabilityInfo)

            val client = createClient(this)

            assertEquals(null, client.getConnectedPhoneName())
        }

    @Test
    fun `getConnectedPhoneName falls back to connected node when capability lookup fails`() =
        runTest {
            val node =
                mockk<Node> {
                    every { displayName } returns "Pixel"
                }
            every { capabilityClient.getCapability(any(), any()) } returns Tasks.forException(IllegalStateException("boom"))
            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(node))

            val client = createClient(this)

            assertEquals("Pixel", client.getConnectedPhoneName())
        }

    @Test
    fun `getConnectedPhoneName returns null when capability and node lookup both fail`() =
        runTest {
            every { capabilityClient.getCapability(any(), any()) } returns Tasks.forException(IllegalStateException("boom"))
            every { nodeClient.connectedNodes } returns Tasks.forException(IllegalStateException("boom"))

            val client = createClient(this)

            assertEquals(null, client.getConnectedPhoneName())
        }

    @Test
    fun `sendMessage returns true when a connected phone node accepts the message`() =
        runTest {
            val phoneNode =
                mockk<Node> {
                    every { id } returns "phone-node"
                }
            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(phoneNode))
            every { messageClient.sendMessage("phone-node", "/logdate/test", "payload".encodeToByteArray()) } returns Tasks.forResult(1)

            val client = createClient(this)
            val success = client.sendMessage(path = "/logdate/test", data = "payload".encodeToByteArray())

            assertTrue(success)
        }

    @Test
    fun `sendMessage returns false when no phone nodes are connected`() =
        runTest {
            every { nodeClient.connectedNodes } returns Tasks.forResult(emptyList())

            val client = createClient(this)
            val success = client.sendMessage(path = "/logdate/test")

            assertFalse(success)
            verify(exactly = 0) { messageClient.sendMessage(any(), any(), any()) }
        }

    @Test
    fun `sendMessage returns false when wearable message client throws`() =
        runTest {
            val phoneNode =
                mockk<Node> {
                    every { id } returns "phone-node"
                }
            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(phoneNode))
            every { messageClient.sendMessage("phone-node", "/logdate/test", any()) } returns
                Tasks.forException(IllegalStateException("boom"))

            val client = createClient(this)

            assertFalse(client.sendMessage(path = "/logdate/test"))
        }

    @Test
    fun `sendMessage returns false when connected node lookup throws`() =
        runTest {
            every { nodeClient.connectedNodes } returns Tasks.forException(IllegalStateException("boom"))

            val client = createClient(this)

            assertFalse(client.sendMessage(path = "/logdate/test"))
        }

    @Test
    fun `downloadAudioFromPhone returns false when request message cannot be sent`() =
        runTest {
            every { nodeClient.connectedNodes } returns Tasks.forResult(emptyList())
            every { channelClient.registerChannelCallback(any<ChannelClient.ChannelCallback>()) } returns Tasks.forResult<Void>(null)
            every { channelClient.unregisterChannelCallback(any<ChannelClient.ChannelCallback>()) } returns Tasks.forResult(false)

            val tempDir = createTempDirectory()
            val destination = tempDir.resolve("phone-note.m4a").toFile()
            val client = createClient(this)

            val success = client.downloadAudioFromPhone(noteId = Uuid.random(), destinationPath = destination.path)

            assertFalse(success)
            assertFalse(destination.exists())
        }

    @Test
    fun `downloadAudioFromPhone returns false when transfer times out`() =
        runTest {
            val noteId = Uuid.random()
            val phoneNode =
                mockk<Node> {
                    every { id } returns "phone-node"
                }
            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(phoneNode))
            every { channelClient.registerChannelCallback(any<ChannelClient.ChannelCallback>()) } returns Tasks.forResult<Void>(null)
            every { channelClient.unregisterChannelCallback(any<ChannelClient.ChannelCallback>()) } returns Tasks.forResult(true)
            every { messageClient.sendMessage("phone-node", WearAudioRequestPaths.audioRequestPath(noteId), any()) } returns
                Tasks.forResult(1)

            val tempDir = createTempDirectory()
            val destination = tempDir.resolve("timed-out-note.m4a").toFile()
            val client = createClient(this, audioTransferTimeoutMs = 10L)

            val download = async { client.downloadAudioFromPhone(noteId = noteId, destinationPath = destination.path) }
            advanceTimeBy(10L)

            assertFalse(download.await())
            assertFalse(destination.exists())
        }

    @Test
    fun `downloadAudioFromPhone writes file after matching transfer channel opens`() =
        runTest {
            val noteId = Uuid.random()
            val transferPath = WearAudioRequestPaths.audioTransferPath(noteId)
            val requestPath = WearAudioRequestPaths.audioRequestPath(noteId)
            val payload = "phone-synced-audio".encodeToByteArray()
            val callback = slot<ChannelClient.ChannelCallback>()
            val phoneNode =
                mockk<Node> {
                    every { id } returns "phone-node"
                }
            val channel =
                mockk<ChannelClient.Channel> {
                    every { path } returns transferPath
                }

            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(phoneNode))
            every { channelClient.registerChannelCallback(capture(callback)) } returns Tasks.forResult<Void>(null)
            every { channelClient.unregisterChannelCallback(any<ChannelClient.ChannelCallback>()) } returns Tasks.forResult(true)
            every { messageClient.sendMessage("phone-node", requestPath, any()) } answers {
                callback.captured.onChannelOpened(channel)
                Tasks.forResult(1)
            }
            every { channelClient.getInputStream(channel) } returns Tasks.forResult(ByteArrayInputStream(payload))
            every { channelClient.close(channel) } returns Tasks.forResult<Void>(null)

            val tempDir = createTempDirectory()
            val destination = tempDir.resolve("downloaded-note.m4a").toFile()
            val client = createClient(this)

            val success = client.downloadAudioFromPhone(noteId = noteId, destinationPath = destination.path)

            assertTrue(success)
            assertContentEquals(payload, destination.readBytes())
        }

    @Test
    fun `downloadAudioFromPhone ignores unrelated channels before matching transfer arrives`() =
        runTest {
            val noteId = Uuid.random()
            val transferPath = WearAudioRequestPaths.audioTransferPath(noteId)
            val requestPath = WearAudioRequestPaths.audioRequestPath(noteId)
            val payload = "phone-synced-audio".encodeToByteArray()
            val callback = slot<ChannelClient.ChannelCallback>()
            val phoneNode =
                mockk<Node> {
                    every { id } returns "phone-node"
                }
            val unrelatedChannel =
                mockk<ChannelClient.Channel> {
                    every { path } returns "/logdate/notes/other/audio"
                }
            val matchingChannel =
                mockk<ChannelClient.Channel> {
                    every { path } returns transferPath
                }

            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(phoneNode))
            every { channelClient.registerChannelCallback(capture(callback)) } returns Tasks.forResult<Void>(null)
            every { channelClient.unregisterChannelCallback(any<ChannelClient.ChannelCallback>()) } returns Tasks.forResult(true)
            every { messageClient.sendMessage("phone-node", requestPath, any()) } answers {
                callback.captured.onChannelOpened(unrelatedChannel)
                callback.captured.onChannelOpened(matchingChannel)
                Tasks.forResult(1)
            }
            every { channelClient.getInputStream(matchingChannel) } returns Tasks.forResult(ByteArrayInputStream(payload))
            every { channelClient.close(matchingChannel) } returns Tasks.forResult<Void>(null)

            val tempDir = createTempDirectory()
            val destination = tempDir.resolve("downloaded-note.m4a").toFile()
            val client = createClient(this)

            val success = client.downloadAudioFromPhone(noteId = noteId, destinationPath = destination.path)

            assertTrue(success)
            assertContentEquals(payload, destination.readBytes())
        }

    @Test
    fun `downloadAudioFromPhone deletes partial file when incoming stream fails`() =
        runTest {
            val noteId = Uuid.random()
            val transferPath = WearAudioRequestPaths.audioTransferPath(noteId)
            val requestPath = WearAudioRequestPaths.audioRequestPath(noteId)
            val callback = slot<ChannelClient.ChannelCallback>()
            val phoneNode =
                mockk<Node> {
                    every { id } returns "phone-node"
                }
            val channel =
                mockk<ChannelClient.Channel> {
                    every { path } returns transferPath
                }

            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(phoneNode))
            every { channelClient.registerChannelCallback(capture(callback)) } returns Tasks.forResult<Void>(null)
            every { channelClient.unregisterChannelCallback(any<ChannelClient.ChannelCallback>()) } returns Tasks.forResult(true)
            every { messageClient.sendMessage("phone-node", requestPath, any()) } answers {
                callback.captured.onChannelOpened(channel)
                Tasks.forResult(1)
            }
            every { channelClient.getInputStream(channel) } returns Tasks.forException(IllegalStateException("boom"))
            every { channelClient.close(channel) } returns Tasks.forResult<Void>(null)

            val tempDir = createTempDirectory()
            val destination = tempDir.resolve("failed-note.m4a").toFile()
            val client = createClient(this)

            val success = client.downloadAudioFromPhone(noteId = noteId, destinationPath = destination.path)

            assertFalse(success)
            assertFalse(destination.exists())
        }

    @Test
    fun `sendFile copies local file bytes into wearable channel output stream`() =
        runTest {
            val payload = "watch-upload".encodeToByteArray()
            val tempDir = createTempDirectory()
            val source = tempDir.resolve("local-note.m4a")
            source.writeBytes(payload)

            val phoneNode =
                mockk<Node> {
                    every { id } returns "phone-node"
                }
            val channel = mockk<ChannelClient.Channel>()
            val outputStream = ByteArrayOutputStream()

            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(phoneNode))
            every { channelClient.openChannel("phone-node", "/logdate/notes/1/audio") } returns Tasks.forResult(channel)
            every { channelClient.getOutputStream(channel) } returns Tasks.forResult(outputStream)
            every { channelClient.close(channel) } returns Tasks.forResult<Void>(null)

            val client = createClient(this)
            val success = client.sendFile(channelPath = "/logdate/notes/1/audio", localFilePath = source.toString())

            assertTrue(success)
            assertContentEquals(payload, outputStream.toByteArray())
        }

    @Test
    fun `sendFile returns false when no phone nodes are connected`() =
        runTest {
            every { nodeClient.connectedNodes } returns Tasks.forResult(emptyList())

            val client = createClient(this)

            assertFalse(client.sendFile(channelPath = "/logdate/notes/1/audio", localFilePath = "/tmp/missing"))
        }

    @Test
    fun `sendFile closes channel and returns false when output stream lookup fails`() =
        runTest {
            val payload = "watch-upload".encodeToByteArray()
            val tempDir = createTempDirectory()
            val source = tempDir.resolve("local-note.m4a")
            source.writeBytes(payload)

            val phoneNode =
                mockk<Node> {
                    every { id } returns "phone-node"
                }
            val channel = mockk<ChannelClient.Channel>()

            every { nodeClient.connectedNodes } returns Tasks.forResult(listOf(phoneNode))
            every { channelClient.openChannel("phone-node", "/logdate/notes/1/audio") } returns Tasks.forResult(channel)
            every { channelClient.getOutputStream(channel) } returns Tasks.forException(IllegalStateException("boom"))
            every { channelClient.close(channel) } returns Tasks.forResult<Void>(null)

            val client = createClient(this)

            assertFalse(client.sendFile(channelPath = "/logdate/notes/1/audio", localFilePath = source.toString()))
            verify(exactly = 1) { channelClient.close(channel) }
        }

    private fun createClient(
        scope: TestScope,
        audioTransferTimeoutMs: Long = 1_000L,
        putDataRequestFactory: WearPutDataRequestFactory? = WearPutDataRequestFactory { _, _ -> mockk(relaxed = true) },
    ): GoogleWearDataLayerClient =
        if (putDataRequestFactory == null) {
            GoogleWearDataLayerClient(
                dataClient = dataClient,
                channelClient = channelClient,
                capabilityClient = capabilityClient,
                nodeClient = nodeClient,
                messageClient = messageClient,
                coroutineScope = scope,
                ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
                audioTransferTimeoutMs = audioTransferTimeoutMs,
            )
        } else {
            GoogleWearDataLayerClient(
                dataClient = dataClient,
                channelClient = channelClient,
                capabilityClient = capabilityClient,
                nodeClient = nodeClient,
                messageClient = messageClient,
                coroutineScope = scope,
                ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
                audioTransferTimeoutMs = audioTransferTimeoutMs,
                putDataRequestFactory = putDataRequestFactory,
            )
        }
}
