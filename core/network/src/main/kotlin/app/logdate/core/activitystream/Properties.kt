package app.logdate.core.activitystream

import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * URI: @id
 *
 * Provides the globally unique identifier for an [Object] or [Link].
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "name": "Foo",
 *   "id": "http://example.org/foo"
 * }
 * ```
 */
typealias Id = String

/**
 * URI: @type
 *
 * Identifies the [Object] or [Link] type. Multiple values may be specified.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "A foo",
 *   "type": "http://example.org/Foo"
 * }
 * ```
 */
typealias Type = String

/**
 * URI: https://www.w3.org/ns/activitystreams#actor
 *
 * Describes one or more entities that either performed or are expected to perform the activity. Any
 * single activity can have multiple `actor`s. The `actor` may be specified using an indirect [Link].
 *
 * Example 1:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally offered the Foo object",
 *   "type": "Offer",
 *   "actor": "http://sally.example.org",
 *   "object": "http://example.org/foo"
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally offered the Foo object",
 *   "type": "Offer",
 *   "actor": {
 *     "type": "Person",
 *     "id": "http://sally.example.org",
 *     "summary": "Sally"
 *   },
 *   "object": "http://example.org/foo"
 * }
 * ```
 *
 * Example 3:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally and Joe offered the Foo object",
 *   "type": "Offer",
 *   "actor": [
 *     "http://joe.example.org",
 *     {
 *       "type": "Person",
 *       "id": "http://sally.example.org",
 *       "name": "Sally"
 *     }
 *   ],
 *   "object": "http://example.org/foo"
 * }
 * ```
 */
sealed interface Actor<T> {
    val type: String
    val name: Name
}

/**
 * Identifies a resource attached or related to an object that potentially requires special handling.
 * The intent is to provide a model that is at least semantically similar to attachments in email.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Note",
 *   "name": "Have you seen my cat?",
 *   "attachment": [
 *     {
 *       "type": "Image",
 *       "content": "This is what he looks like.",
 *       "url": "http://example.org/cat.jpeg"
 *     }
 *   ]
 * }
 * ```
 */
interface Attachment<T>

/**
 * URI: https://www.w3.org/ns/activitystreams#attributedTo
 *
 * Identifies one or more entities to which this object is attributed. The attributed entities might
 * not be Actors. For instance, an object might be attributed to the completion of another activity.
 *
 * Example 1:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Image",
 *   "name": "My cat taking a nap",
 *   "url": "http://example.org/cat.jpeg",
 *   "attributedTo": [
 *     {
 *       "type": "Person",
 *       "name": "Sally"
 *     }
 *   ]
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Image",
 *   "name": "My cat taking a nap",
 *   "url": "http://example.org/cat.jpeg",
 *   "attributedTo": [
 *     "http://joe.example.org",
 *     {
 *       "type": "Person",
 *       "name": "Sally"
 *     }
 *   ]
 * }
 * ```
 */
interface AttributedTo<T>

/**
 * URI: https://www.w3.org/ns/activitystreams#audience
 *
 * Identifies one or more entities that represent the total population of entities for which the
 * object can considered to be relevant.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "name": "Holiday announcement",
 *   "type": "Note",
 *   "content": "Thursday will be a company-wide holiday. Enjoy your day off!",
 *   "audience": {
 *     "type": "http://example.org/Organization",
 *     "name": "ExampleCo LLC"
 *   }
 * }
 * ```
 */
interface Audience<T>

/**
 * The content or textual representation of the Object encoded as a JSON string. By default, the
 * value of `content` is HTML. The [Object.mediaType] property can be used in the object to indicate
 * a different content type.
 *
 * The content may be expressed using multiple language-tagged values.
 *
 * Range `xsd:string | rdf:langString`
 *
 * Example 1:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "A simple note",
 *   "type": "Note",
 *   "content": "A <em>simple</em> note"
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "A simple note",
 *   "type": "Note",
 *   "contentMap": {
 *     "en": "A <em>simple</em> note",
 *     "es": "Una nota <em>sencilla</em>",
 *     "zh-Hans": "一段<em>简单的</em>笔记"
 *   }
 * }
 * ```
 *
 * Example 3:
 * ```
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "A simple note",
 *   "type": "Note",
 *   "mediaType": "text/markdown",
 *   "content": "## A simple note\nA simple markdown `note`"
 * }
 * ```
 */
typealias Content = String

/**
 * URI: https://www.w3.org/ns/activitystreams#context
 *
 * Identifies the context within which the object exists or an activity was performed.
 *
 * The notion of "context" used is intentionally vague. The intended function is to serve as a means
 * of grouping objects and activities that share a common originating context or purpose. An example
 * could be all activities relating to a common project or event.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Activities in context 1",
 *   "type": "Collection",
 *   "items": [
 *     {
 *       "type": "Offer",
 *       "actor": "http://sally.example.org",
 *       "object": "http://example.org/posts/1",
 *       "target": "http://john.example.org",
 *       "context": "http://example.org/contexts/1"
 *     },
 *     {
 *       "type": "Like",
 *       "actor": "http://joe.example.org",
 *       "object": "http://example.org/posts/2",
 *       "context": "http://example.org/contexts/1"
 *     }
 *   ]
 * }
 * ```
 */
typealias Context = String

/**
 * URI: https://www.w3.org/ns/activitystreams#endTime
 *
 * The date and time describing the actual or expected ending time of the object. When used with an
 * [Activity] object, for instance, the [Object.endTime] property specifies the moment the activity
 * concluded or is expected to conclude.
 *
 * Range: `xsd:dateTime`
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Event",
 *   "name": "Going-Away Party for Jim",
 *   "startTime": "2014-12-31T23:00:00-08:00",
 *   "endTime": "2015-01-01T06:00:00-08:00"
 * }
 * ```
 */
typealias EndTime = Instant

/**
 * URI: https://www.w3.org/ns/activitystreams#name
 *
 * A simple, human-readable, plain-text name for the object. HTML markup must not be included. The
 * name may be expressed using multiple language-tagged values.
 *
 * Range: `xsd:string | rdf:langString`
 *
 * Example 1:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Note",
 *   "name": "A simple note"
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Note",
 *   "nameMap": {
 *     "en": "A simple note",
 *     "es": "Una nota sencilla",
 *     "zh-Hans": "一段简单的笔记"
 *   }
 * }
 * ```
 */
typealias Name = String

/**
 * Identifies the entity (e.g. an application) that generated the object.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "A simple note",
 *   "type": "Note",
 *   "content": "This is all there is.",
 *   "generator": {
 *     "type": "Application",
 *     "name": "Exampletron 3000"
 *   }
 * }
 * ```
 */
interface Generator {
    val type: String
    val name: Name
}

/**
 * URI: https://www.w3.org/ns/activitystreams#icon
 *
 * Indicates an entity that describes an icon for this object. The image should have an aspect ratio
 * of one (horizontal) to one (vertical) and should be suitable for presentation at a small size.
 *
 * Example 1:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "A simple note",
 *   "type": "Note",
 *   "content": "This is all there is.",
 *   "icon": {
 *     "type": "Image",
 *     "name": "Note icon",
 *     "url": "http://example.org/note.png",
 *     "width": 16,
 *     "height": 16
 *   }
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "A simple note",
 *   "type": "Note",
 *   "content": "A simple note",
 *   "icon": [
 *     {
 *       "type": "Image",
 *       "summary": "Note (16x16)",
 *       "url": "http://example.org/note1.png",
 *       "width": 16,
 *       "height": 16
 *     },
 *     {
 *       "type": "Image",
 *       "summary": "Note (32x32)",
 *       "url": "http://example.org/note2.png",
 *       "width": 32,
 *       "height": 32
 *     }
 *   ]
 * }
 * ```
 */
sealed interface Icon {
    interface ImageIcon : Icon, Image
    interface LinkIcon : Icon, Link
}

/**
 * URI: https://www.w3.org/ns/activitystreams#location
 *
 * Indicates one or more physical or logical locations associated with the object.
 */
interface Location {
    val type: String
    val name: Name
    val longitude: Longitude
    val latitude: Latitude
    val altitude: Altitude
    val units: Units
}

interface InReplyTo<T>

/**
 * URI: [https://www.w3.org/ns/activitystreams#preview](https://www.w3.org/ns/activitystreams#preview)
 *
 * Identifies an entity that provides a preview of this object.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Video",
 *   "name": "Cool New Movie",
 *   "duration": "PT2H30M",
 *   "preview": {
 *     "type": "Video",
 *     "name": "Trailer",
 *     "duration": "PT1M",
 *     "url": {
 *       "href": "http://example.org/trailer.mkv",
 *       "mediaType": "video/mkv"
 *     }
 *   }
 * }
 */
interface Preview<T>

/**
 * URI: https://www.w3.org/ns/activitystreams#published
 *
 * The date and time at which the object was published.
 *
 * Range: `xsd:dateTime`
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "A simple note",
 *   "type": "Note",
 *   "content": "Fish swim.",
 *   "published": "2014-12-12T12:12:12Z"
 * }
 * ```
 */
typealias Published = DateTime

/**
 * URI: https://www.w3.org/ns/activitystreams#replies
 *
 * Identifies a [Collection] containing objects considered to be responses to this object.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "A simple note",
 *   "type": "Note",
 *   "id": "http://www.test.example/notes/1",
 *   "content": "I am fine.",
 *   "replies": {
 *     "type": "Collection",
 *     "totalItems": 1,
 *     "items": [
 *       {
 *         "summary": "A response to the note",
 *         "type": "Note",
 *         "content": "I am glad to hear it.",
 *         "inReplyTo": "http://www.test.example/notes/1"
 *       }
 *     ]
 *   }
 * }
 * ```
 */
typealias Replies = Collection

/**
 * URI: https://www.w3.org/ns/activitystreams#startTime
 *
 * The date and time describing the actual or expected starting time of the object. When used with an
 * [Activity] object, for instance, the [Object.startTime] property specifies the moment the activity
 * began or is scheduled to begin.
 *
 * Range: `xsd:dateTime`
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Event",
 *   "name": "Going-Away Party for Jim",
 *   "startTime": "2014-12-31T23:00:00-08:00",
 *   "endTime": "2015-01-01T06:00:00-08:00"
 * }
 * ```
 */
typealias StartTime = Instant

/**
 * URI: https://www.w3.org/ns/activitystreams#summary
 *
 * A natural language summarization of the object encoded as HTML. Multiple language-tagged values
 * may be provided.
 *
 * Range: `xsd:string | rdf:langString`
 *
 * Example 1:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "name": "Cane Sugar Processing",
 *   "type": "Note",
 *   "summary": "A simple <em>note</em>"
 * }
 *  ```
 *
 *  Example 2:
 *  ```json
 *  {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "name": "Cane Sugar Processing",
 *   "type": "Note",
 *   "summaryMap": {
 *     "en": "A simple <em>note</em>",
 *     "es": "Una <em>nota</em> sencilla",
 *     "zh-Hans": "一段<em>简单的</em>笔记"
 *   }
 * }
 * ```
 */
typealias Summary = String

/**
 * URI: https://www.w3.org/ns/activitystreams#tag
 *
 * One or more "tags" that have been associated with an objects. A tag can be any kind of Object.
 * The key difference between `attachment` and `tag` is that the former implies association by
 * inclusion, while the latter implies associated by reference.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Image",
 *   "summary": "Picture of Sally",
 *   "url": "http://example.org/sally.jpg",
 *   "tag": [
 *     {
 *       "type": "Person",
 *       "id": "http://sally.example.org",
 *       "name": "Sally"
 *     }
 *   ]
 * }
 * ```
 */
interface Tag<T>

/**
 * URI: https://www.w3.org/ns/activitystreams#updated
 *
 * The date and time at which the object was updated.
 *
 * Range: `xsd:dateTime`
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "name": "Cranberry Sauce Idea",
 *   "type": "Note",
 *   "content": "Mush it up so it does not have the same shape as the can.",
 *   "updated": "2014-12-12T12:12:12Z"
 * }
 * ```
 */
typealias Updated = Instant

/**
 * URI: https://www.w3.org/ns/activitystreams#url
 *
 * Identifies one or more links to representations of the object.
 *
 * Range: `xsd:anyURI | Link`
 *
 * Example 1:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Document",
 *   "name": "4Q Sales Forecast",
 *   "url": "http://example.org/4q-sales-forecast.pdf"
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Document",
 *   "name": "4Q Sales Forecast",
 *   "url": {
 *     "type": "Link",
 *     "href": "http://example.org/4q-sales-forecast.pdf"
 *   }
 * }
 * ```
 *
 * Example 3:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Document",
 *   "name": "4Q Sales Forecast",
 *   "url": [
 *     {
 *       "type": "Link",
 *       "href": "http://example.org/4q-sales-forecast.pdf",
 *       "mediaType": "application/pdf"
 *     },
 *     {
 *       "type": "Link",
 *       "href": "http://example.org/4q-sales-forecast.html",
 *       "mediaType": "text/html"
 *     }
 *   ]
 * }
 * ```
 */
interface Url<T>

/**
 * URI: https://www.w3.org/ns/activitystreams#to
 *
 * Identifies an entity considered to be part of the public primary audience of an Object.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally offered the post to John",
 *   "type": "Offer",
 *   "actor": "http://sally.example.org",
 *   "object": "http://example.org/posts/1",
 *   "target": "http://john.example.org",
 *   "to": [ "http://joe.example.org" ]
 * }
 * ```
 */
interface To<T>

/**
 * URI: https://www.w3.org/ns/activitystreams#bto
 *
 * Identifies an Object that is part of the private primary audience of this Object.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally offered a post to John",
 *   "type": "Offer",
 *   "actor": "http://sally.example.org",
 *   "object": "http://example.org/posts/1",
 *   "target": "http://john.example.org",
 *   "bto": [ "http://joe.example.org" ]
 * }
 * ```
 */
interface Bto<T>

/**
 * URI: https://www.w3.org/ns/activitystreams#cc
 *
 * Identifies an Object that is part of the public secondary audience of this Object.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally offered a post to John",
 *   "type": "Offer",
 *   "actor": "http://sally.example.org",
 *   "object": "http://example.org/posts/1",
 *   "target": "http://john.example.org",
 *   "cc": [ "http://joe.example.org" ]
 * }
 * ```
 */
interface Cc<T>

/**
 * URI: https://www.w3.org/ns/activitystreams#bcc
 *
 * Identifies one or more Objects that are part of the private secondary audience of this Object.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally offered a post to John",
 *   "type": "Offer",
 *   "actor": "http://sally.example.org",
 *   "object": "http://example.org/posts/1",
 *   "target": "http://john.example.org",
 *   "bcc": [ "http://joe.example.org" ]
 * }
 * ```
 */
interface Bcc<T>

/**
 * URI: [https://www.w3.org/ns/activitystreams#mediaType](https://www.w3.org/ns/activitystreams#mediaType)
 *
 * When used on a [Link], identifies the MIME media type of the referenced resource.
 *
 * When used on an [Object], identifies the MIME media type of the value of the `content` property. If
 * not specified, the `content` property is assumed to contain text/html content.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Link",
 *   "href": "http://example.org/abc",
 *   "hreflang": "en",
 *   "mediaType": "text/html",
 *   "name": "Next"
 * }
 * ```
 */
typealias MediaType = String

/**
 * URI: [https://www.w3.org/ns/activitystreams#duration](https://www.w3.org/ns/activitystreams#duration)
 *
 * When the object describes a time-bound resource, such as an audio or video, a meeting, etc, the
 * `duration` property indicates the object's approximate duration. The value must be expressed as
 * an `xsd:duration` as defined by [xmlschema11-2](https://www.w3.org/TR/activitystreams-vocabulary/#bib-xmlschema11-2),
 * section 3.3.6 (e.g. a period of 5 seconds is represented as "`PT5S`").
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Video",
 *   "name": "Puppy Plays With Ball",
 *   "duration": "PT1M"
 * }
 * ```
 */
typealias Duration = Duration

/**
 * URI: [https://www.w3.org/ns/activitystreams#mediaType](https://www.w3.org/ns/activitystreams#mediaType)
 *
 * On a [Relationship] object, the subject property identifies one of the connected individuals.
 * For instance, for a [Relationship] object describing "John is related to Sally", subject would
 * refer to John.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally is an acquaintance of John's",
 *   "type": "Relationship",
 *   "subject": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "relationship": "http://purl.org/vocab/relationship/acquaintanceOf",
 *   "object": {
 *     "type": "Person",
 *     "name": "John"
 *   }
 * }
 * ```
 */
interface Subject<T>


/**
 * Identifies an exclusive option for a Question. Use of [OneOf] implies that the Question can have
 * only a single answer. To indicate that a Question can have multiple answers, use [AnyOf].
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Question",
 *   "name": "What is the answer?",
 *   "oneOf": [
 *     {
 *       "type": "Note",
 *       "name": "Option A"
 *     },
 *     {
 *       "type": "Note",
 *       "name": "Option B"
 *     }
 *   ]
 * }
 * ```
 */
interface OneOf<T>

/**
 * Identifies an inclusive option for a Question. Use of anyOf implies that the Question can have
 * multiple answers. To indicate that a Question can have only one answer, use [OneOf].
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Question",
 *   "name": "What is the answer?",
 *   "anyOf": [
 *     {
 *       "type": "Note",
 *       "name": "Option A"
 *     },
 *     {
 *       "type": "Note",
 *       "name": "Option B"
 *     }
 *   ]
 * }
 * ```
 */
interface AnyOf<T>

/**
 * Indicates that a question has been closed, and answers are no longer accepted.
 *
 * Range: Object | Link | xsd:dateTime | xsd:boolean
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Question",
 *   "name": "What is the answer?",
 *   "closed": "2016-05-10T00:00:00Z"
 * }
 * ```
 */
interface Closed<T> {
    // TODO: Find more elegant solution for this
    data class DateTimeClosed(val value: Instant) : Closed<DateTime>
    data class BooleanClosed(val value: Boolean) : Closed<Boolean>
}

sealed interface Target<T>
sealed interface Result<T>
sealed interface Origin<T>
sealed interface Instrument<T>

/**
 * URI: [https://www.w3.org/ns/activitystreams#first](https://www.w3.org/ns/activitystreams#first)
 *
 * In a paged Collection, indicates the furthest preceding page of items in the collection.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally's blog posts",
 *   "type": "Collection",
 *   "totalItems": 3,
 *   "first": "http://example.org/collection?page=0"
 * }
 * ```
 */
sealed interface First<T>

/**
 * URI: [https://www.w3.org/ns/activitystreams#last](https://www.w3.org/ns/activitystreams#last)
 *
 * In a paged [Collection], indicates the furthest following page of items in the collection.
 */
sealed interface Last<T>

/**
 * In a paged Collection, indicates the page that contains the most recently updated member items.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally's blog posts",
 *   "type": "Collection",
 *   "totalItems": 3,
 *   "current": "http://example.org/collection",
 *   "items": [
 *     "http://example.org/posts/1",
 *     "http://example.org/posts/2",
 *     "http://example.org/posts/3"
 *   ]
 * }
 * ```
 */
sealed interface Current<T>

/**
 * Identifies the items contained in a collection. The items might be ordered or unordered.
 *
 * TODO: Support Ordered List of [Object | Link ] instances
 */
sealed interface Items<T>

/**
 * URI: [https://www.w3.org/ns/activitystreams#partOf](https://www.w3.org/ns/activitystreams#partOf)
 *
 * Identifies the [Collection] to which a [CollectionPage] objects items belong.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Page 1 of Sally's notes",
 *   "type": "CollectionPage",
 *   "id": "http://example.org/collection?page=1",
 *   "partOf": "http://example.org/collection",
 *   "items": [
 *     {
 *       "type": "Note",
 *       "name": "Pizza Toppings to Try"
 *     },
 *     {
 *       "type": "Note",
 *       "name": "Thought about California"
 *     }
 *   ]
 * }
 * ```
 */
sealed interface PartOf<T>

/**
 * URI: [https://www.w3.org/ns/activitystreams#next](https://www.w3.org/ns/activitystreams#next)
 *
 * In a paged [Collection], indicates the next page of items.
 *
 * Example 1:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Page 2 of Sally's blog posts",
 *   "type": "CollectionPage",
 *   "next": "http://example.org/collection?page=2",
 *   "items": [
 *     "http://example.org/posts/1",
 *     "http://example.org/posts/2",
 *     "http://example.org/posts/3"
 *   ]
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Page 2 of Sally's blog posts",
 *   "type": "CollectionPage",
 *   "next": {
 *     "type": "Link",
 *     "name": "Next Page",
 *     "href": "http://example.org/collection?page=2"
 *   },
 *   "items": [
 *     "http://example.org/posts/1",
 *     "http://example.org/posts/2",
 *     "http://example.org/posts/3"
 *   ]
 * }
 * ```
 */
sealed interface Next<T>

/**
 * URI: [https://www.w3.org/ns/activitystreams#prev](https://www.w3.org/ns/activitystreams#prev)
 *
 * In a paged [Collection], indicates the previous page of items.
 *
 * Example 1:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Page 1 of Sally's blog posts",
 *   "type": "CollectionPage",
 *   "prev": "http://example.org/collection?page=1",
 *   "items": [
 *     "http://example.org/posts/1",
 *     "http://example.org/posts/2",
 *     "http://example.org/posts/3"
 *   ]
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Page 1 of Sally's blog posts",
 *   "type": "CollectionPage",
 *   "prev": {
 *     "type": "Link",
 *     "name": "Previous Page",
 *     "href": "http://example.org/collection?page=1"
 *   },
 *   "items": [
 *     "http://example.org/posts/1",
 *     "http://example.org/posts/2",
 *     "http://example.org/posts/3"
 *   ]
 * }
 * ```
 */
sealed interface Prev<T>

/**
 * A non-negative integer specifying the total number of objects contained by the logical view of
 * the collection. This number might not reflect the actual number of items serialized within the
 * [Collection] object instance.
 */
typealias TotalItems = Int