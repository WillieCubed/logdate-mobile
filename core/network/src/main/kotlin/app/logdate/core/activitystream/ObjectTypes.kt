package app.logdate.core.activitystream

/**
 * Describes a relationship between two individuals. The subject and object properties are used to
 * identify the connected individuals.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally is an acquaintance of John",
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
interface Relationship : Object {
    val subject: Subject<*>
    val `object`: Object
    val relationship: Relationship
}

/**
 * Represents any kind of multi-paragraph written work.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Article",
 *   "name": "What a Crazy Day I Had",
 *   "content": "<div>... you will never believe ...</div>",
 *   "attributedTo": "http://sally.example.org"
 * }
 * ```
 */
interface Article : Object

/**
 * Represents a document of any kind.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Document",
 *   "name": "4Q Sales Forecast",
 *   "url": "http://example.org/4q-sales-forecast.pdf"
 * }
 * ```
 */
interface Document : Object

/**
 * Represents an audio document of any kind.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Audio",
 *   "name": "Interview With A Famous Technologist",
 *   "url": {
 *     "type": "Link",
 *     "href": "http://example.org/podcast.mp3",
 *     "mediaType": "audio/mp3"
 *   }
 * }
 * ```
 */
interface Audio : Document

/**
 * An image document of any kind.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Audio",
 *   "name": "Interview With A Famous Technologist",
 *   "url": {
 *     "type": "Link",
 *     "href": "http://example.org/podcast.mp3",
 *     "mediaType": "audio/mp3"
 *   }
 * }
 * ```
 */
interface Image : Document

/**
 * Represents a video document of any kind.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Video",
 *   "name": "Puppy Plays With Ball",
 *   "url": "http://example.org/video.mkv",
 *   "duration": "PT2H"
 * }
 * ```
 */
interface Video : Document


/**
 * Represents a short written work typically less than a single paragraph in length.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Note",
 *   "name": "A Word of Warning",
 *   "content": "Looks like it is going to rain today. Bring an umbrella!"
 * }
 * ```
 */
interface Note : Object

/**
 * 	Represents a Web Page.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Page",
 *   "name": "Omaha Weather Report",
 *   "url": "http://example.org/weather-in-omaha.html"
 * }
 * ```
 */
interface Page : Document

/**
 * Represents any kind of event.
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
interface Event : Object

/**
 * Represents a logical or physical location. See [5.3 Representing Places](https://www.w3.org/TR/activitystreams-vocabulary/#places)
 * for additional information.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Place",
 *   "name": "Work"
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Place",
 *   "name": "Fresno Area",
 *   "latitude": 36.75,
 *   "longitude": 119.7667,
 *   "radius": 15,
 *   "units": "miles"
 * }
 * ```
 */
interface Place : Object {
    val accuracy: Accuracy
    val altitude: Altitude
    val latitude: Latitude
    val longitude: Longitude
    val radius: Radius
    val units: Units
}

/**
 * Indicates the accuracy of position coordinates on a [Place] object. Expressed in properties of
 * percentage. e.g. "94.0" means "94.0% accurate".
 *
 * Range: `xsd:float` [>= 0.0f, <= 100.0f]
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "name": "Liu Gu Lu Cun, Pingdu, Qingdao, Shandong, China",
 *   "type": "Place",
 *   "latitude": 36.75,
 *   "longitude": 119.7667,
 *   "accuracy": 94.5
 * }
 * ```
 */
typealias Accuracy = Float

/**
 * Indicates the altitude of a place. The measurement units is indicated using the `units` property.
 * If `units` is not specified, the default is assumed to be "m" indicating meters.
 *
 * Range: `xsd:float`
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Place",
 *   "name": "Fresno Area",
 *   "altitude": 15.0,
 *   "latitude": 36.75,
 *   "longitude": 119.7667,
 *   "units": "miles"
 * }
 * ```
 */
typealias Altitude = Float

/**
 * The latitude of a Place.
 *
 * Range: `xsd:float`
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Place",
 *   "name": "Fresno Area",
 *   "latitude": 36.75,
 *   "longitude": 119.7667,
 *   "radius": 15,
 *   "units": "miles"
 * }
 * ```
 */
typealias Latitude = Float

/**
 * The longitude of a Place.
 *
 * Range: `xsd:float`
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Place",
 *   "name": "Fresno Area",
 *   "latitude": 36.75,
 *   "longitude": 119.7667,
 *   "radius": 15,
 *   "units": "miles"
 * }
 * ```
 */
typealias Longitude = Float

/**
 * The radius from the given latitude and longitude for a Place. The units is expressed by the
 * units property. If units is not specified, the default is assumed to be "m" indicating "meters".
 *
 * Range: `xsd:float` [>= 0.0f]
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Place",
 *   "name": "Fresno Area",
 *   "latitude": 36.75,
 *   "longitude": 119.7667,
 *   "radius": 15,
 *   "units": "miles"
 * }
 * ```
 */
typealias Radius = Float

/**
 * Specifies the measurement units for the `radius` and `altitude` properties on a [Place] object.
 * If not specified, the default is assumed to be "m" for "meters".
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Place",
 *   "name": "Fresno Area",
 *   "latitude": 36.75,
 *   "longitude": 119.7667,
 *   "radius": 15,
 *   "units": "miles"
 * }
 * ```
 */
@Suppress("EnumEntryName")
enum class Units(val value: String) {
    cm("cm"), feet("feet"), inches("inches"), km("km"), m("m"), miles("miles"),
}

/**
 * A [Profile] is a content object that describes another [Object], typically used to describe Actor
 * Type objects. The `describes` property is used to reference the object being described by the
 * profile.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Profile",
 *   "summary": "Sally's Profile",
 *   "describes": {
 *     "type": "Person",
 *     "name": "Sally Smith"
 *   }
 * }
 * ```
 */
interface Profile : Object

/**
 * A Tombstone represents a content object that has been deleted. It can be used in [Collection]s to
 * signify that there used to be an object at this position, but it has been deleted.
 *
 * Example:
 * ```json
 * {
 *   "type": "OrderedCollection",
 *   "totalItems": 3,
 *   "name": "Vacation photos 2016",
 *   "orderedItems": [
 *     {
 *       "type": "Image",
 *       "id": "http://image.example/1"
 *     },
 *     {
 *       "type": "Tombstone",
 *       "formerType": "Image",
 *       "id": "http://image.example/2",
 *       "deleted": "2016-03-17T00:00:00Z"
 *     },
 *     {
 *       "type": "Image",
 *       "id": "http://image.example/3"
 *     }
 *   ]
 * }
 * ```
 */
interface Tombstone : Object {
    val formerType: FormerType
    val deleted: Deleted
}

/**
 * On a [Tombstone] object, the `formerType` property is used to indicate the type of the object
 * that was deleted.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "This image has been deleted",
 *   "type": "Tombstone",
 *   "formerType": "Image",
 *   "url": "http://example.org/image/2"
 * }
 * ```
 */
typealias FormerType = Object

/**
 * On a [Tombstone] object, the `deleted` property is a timestamp for when the object was deleted.
 *
 * Range: `xsd:dateTime`
 *
 * Example:
 * ```json
 * {
 * "@context": "https://www.w3.org/ns/activitystreams",
 * "summary": "This image has been deleted",
 * "type": "Tombstone",
 * "deleted": "2016-05-03T00:00:00Z"
 * }
 * ```
 */
typealias Deleted = DateTime

/**
 * An instance of `xsd:dateTime`
 */
typealias DateTime = String