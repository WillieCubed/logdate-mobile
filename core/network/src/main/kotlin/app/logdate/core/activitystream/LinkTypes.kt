package app.logdate.core.activitystream

/**
 * A specialized Link that represents an @mention.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Mention of Joe by Carrie in her note",
 *   "type": "Mention",
 *   "href": "http://example.org/joe",
 *   "name": "Joe"
 * }
 * ```
 */
interface Mention : Link