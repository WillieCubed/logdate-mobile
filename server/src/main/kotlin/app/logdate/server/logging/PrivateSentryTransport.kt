package app.logdate.server.logging

import io.sentry.Hint
import io.sentry.ISerializer
import io.sentry.SentryEnvelope
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryEnvelopeItem
import io.sentry.SentryItemType
import io.sentry.transport.ITransport

/** The SDK adds trace context and attachments after beforeSend; rebuild at the final boundary. */
internal class PrivateSentryTransport(
    private val delegate: ITransport,
    private val serializer: ISerializer,
    private val release: String,
    private val environment: String,
) : ITransport by delegate {
    override fun send(
        envelope: SentryEnvelope,
        hint: Hint,
    ) {
        for (item in envelope.items) {
            if (item.header.type != SentryItemType.Event) continue
            val original = item.getEvent(serializer) ?: continue
            val safe = sanitizedServerCrash(original, release, environment)
            val output =
                SentryEnvelope(
                    SentryEnvelopeHeader(safe.eventId),
                    listOf(SentryEnvelopeItem.fromEvent(serializer, safe)),
                )
            delegate.send(output, Hint())
        }
    }
}
