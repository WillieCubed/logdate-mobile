package app.logdate.core.network

import app.logdate.core.activitystream.OrderedCollection

interface ActivityPubClient {
    val inbox: OrderedCollection
    val outbox: OrderedCollection
}