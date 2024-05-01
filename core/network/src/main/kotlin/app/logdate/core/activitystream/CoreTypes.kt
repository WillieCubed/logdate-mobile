package app.logdate.core.activitystream

interface ActivityStreamBase {
    val nsContext: String
    val id: String
}

/**
 * URI: [https://www.w3.org/ns/activitystreams#Object](https://www.w3.org/ns/activitystreams#Object)
 *
 * Describes an object of any kind. The Object type serves as the base type for most of the other
 * kinds of objects defined in the Activity Vocabulary, including other Core types such as
 * [Activity], [IntransitiveActivity], [Collection] and [OrderedCollection].
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Object",
 *   "id": "http://www.test.example/object/1",
 *   "name": "A Simple, non-specific object"
 * }
 * ```
 */
interface Object : ActivityStreamBase, Actor<Object>, Attachment<Object>, AttributedTo<Object>,
    Target<Object>, Result<Object>, Origin<Object>, Instrument<Object>, Items<Object>,
    Subject<Object>, OneOf<Object>, AnyOf<Object>, Closed<Object>, Tag<Object>, To<Object>,
    Bto<Object>, Cc<Object>, Bcc<Object>, Preview<Object> {
    val attachment: Attachment<*>
    val attributedTo: AttributedTo<*>
    val audience: Audience<*>
    val content: Content
    val context: Context
    val endTime: EndTime
    val generator: Generator
    val icon: Icon
    val image: Image
    val inReplyTo: InReplyTo<*>
    val location: Location
    val preview: Preview<*>
    val published: Published
    val replies: Replies
    val startTime: StartTime
    val summary: Summary
    val update: Updated
    val tag: Tag<*>
    val url: Url<*>
    val to: To<*>
    val bto: Bto<*>
    val cc: Cc<*>
    val bcc: Bcc<*>
    val mediaType: MediaType
    val duration: Duration
}

/**
 * URI: [https://www.w3.org/ns/activitystreams#Link](https://www.w3.org/ns/activitystreams#Link)
 *
 * A Link is an indirect, qualified reference to a resource identified by a URL. The fundamental
 * model for links is established by [RFC5988](https://www.w3.org/TR/activitystreams-vocabulary/#bib-RFC5988).
 * Many of the properties defined by the Activity Vocabulary allow values that are either instances of Object or Link. When a Link is used, it establishes a qualified relation connecting the subject (the containing object) to the resource identified by the href. Properties of the Link are properties of the reference as opposed to properties of the resource.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Link",
 *   "href": "http://example.org/abc",
 *   "hreflang": "en",
 *   "mediaType": "text/html",
 *   "name": "An example link"
 * }
 * ```
 */
interface Link : ActivityStreamBase, Actor<Link>, Attachment<Object>, AttributedTo<Link>,
    Target<Link>, Result<Link>, Origin<Link>, Instrument<Link>, First<Link>, Last<Link>,
    Current<Link>, Items<Link>, Subject<Link>, OneOf<Link>, AnyOf<Link>, Closed<Link>, Tag<Link>,
    Url<Link>, To<Link>, Bto<Link>, Cc<Link>, Bcc<Link>, Preview<Link>, PartOf<Link>, Next<Link>,
    Prev<Link> {
    val href: String
    val rel: String
    val mediaType: MediaType
    val hreflang: String
    val height: Int
    val width: Int
    val preview: Link
}

interface BaseActivity : Object {
    val actor: Actor<*>
    val target: Target<*>
    val result: Result<*>
    val origin: Origin<*>
    val instrument: Instrument<*>
}

/**
 * URI: [https://www.w3.org/ns/activitystreams#Actor](https://www.w3.org/ns/activitystreams#Actor)
 *
 * An Activity is a subtype of [Object] that describes some form of action that may happen, is
 * currently happening, or has already happened. The `Activity` type itself serves as an abstract base
 * type for all types of activities. It is important to note that the `Activity` type itself does not
 * carry any specific semantics about the kind of action being taken.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Activity",
 *   "summary": "Sally did something to a note",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Note",
 *     "name": "A Note"
 *   }
 * }
 * ```
 */
interface Activity : BaseActivity {
    val `object`: Object
}

/**
 * URI: [https://www.w3.org/ns/activitystreams#IntransitiveActivity](https://www.w3.org/ns/activitystreams#IntransitiveActivity)
 *
 * Instances of `IntransitiveActivity` are a subtype of Activity representing intransitive actions.
 * The `object` property is therefore inappropriate for these activities.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Travel",
 *   "summary": "Sally went to work",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "target": {
 *     "type": "Place",
 *     "name": "Work"
 *   }
 * }
 * ```
 */
interface IntransitiveActivity : BaseActivity

/**
 * URI: [https://www.w3.org/ns/activitystreams#Collection](https://www.w3.org/ns/activitystreams#Collection)
 *
 * A Collection is a subtype of Object that represents ordered or unordered sets of [Object] or
 * [Link] instances.
 *
 * Refer to the Activity Streams 2.0 Core specification for a complete description of the
 * [Collection] type.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally's notes",
 *   "type": "Collection",
 *   "totalItems": 2,
 *   "items": [
 *     {
 *       "type": "Note",
 *       "name": "A Simple Note"
 *     },
 *     {
 *       "type": "Note",
 *       "name": "Another Simple Note"
 *     }
 *   ]
 * }
 * ```
 */
interface Collection : Object, PartOf<Collection> {
    val totalItems: TotalItems
    val current: Current<*>
    val first: First<*>
    val last: Last<*>
    val items: Items<*>
}

/**
 * URI: [https://www.w3.org/ns/activitystreams#OrderedCollection](https://www.w3.org/ns/activitystreams#OrderedCollection)
 *
 * A subtype of Collection in which members of the logical collection are assumed to always be
 * strictly ordered.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally's notes",
 *   "type": "OrderedCollection",
 *   "totalItems": 2,
 *   "orderedItems": [
 *     {
 *       "type": "Note",
 *       "name": "A Simple Note"
 *     },
 *     {
 *       "type": "Note",
 *       "name": "Another Simple Note"
 *     }
 *   ]
 * }
 * ```
 */
interface OrderedCollection : Collection {
    val orderedItems: List<Object> // TODO: Is this part of spec?
}

/**
 * URI: [https://www.w3.org/ns/activitystreams#CollectionPage](https://www.w3.org/ns/activitystreams#CollectionPage)
 *
 * Used to represent distinct subsets of items from a [Collection]. Refer to the [Activity Streams 2.0 Core](https://www.w3.org/TR/activitystreams-core/#dfn-collectionpage)
 * for a complete description of the `CollectionPage` object.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Page 1 of Sally's notes",
 *   "type": "CollectionPage",
 *   "id": "http://example.org/foo?page=1",
 *   "partOf": "http://example.org/foo",
 *   "items": [
 *     {
 *       "type": "Note",
 *       "name": "A Simple Note"
 *     },
 *     {
 *       "type": "Note",
 *       "name": "Another Simple Note"
 *     }
 *   ]
 * }
 * ```
 */
interface CollectionPage : Collection, Current<CollectionPage>, First<CollectionPage>,
    Last<CollectionPage>, Next<CollectionPage>, Prev<CollectionPage> {
    val partOf: PartOf<*>
    val next: Next<*>
    val prev: Prev<*>
}

/**
 *
 * Used to represent ordered subsets of items from an [OrderedCollection]. Refer to the Activity
 * Streams 2.0 Core for a complete description of the [OrderedCollectionPage] object.
 */
interface OrderedCollectionPage : CollectionPage, OrderedCollection {
    val startIndex: Int
}