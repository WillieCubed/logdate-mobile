package app.logdate.core.activitypub

import app.logdate.core.activitystream.OrderedCollection

interface ActivityPubClient {
    val inbox: OrderedCollection
    val outbox: OrderedCollection
}