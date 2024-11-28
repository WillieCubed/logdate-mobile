package app.logdate.shared.activitypub.core

import app.logdate.shared.activitypub.streams.OrderedCollection

interface ActivityPubClient {
    val inbox: OrderedCollection
    val outbox: OrderedCollection
}