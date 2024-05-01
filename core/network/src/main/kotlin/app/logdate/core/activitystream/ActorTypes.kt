package app.logdate.core.activitystream

/**
 * Describes a software application.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Application",
 *   "name": "Exampletron 3000"
 * }
 * ```
 */
interface Application : Object

/**
 * Represents a formal or informal collective of Actors.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Group",
 *   "name": "Big Beards of Austin"
 * }
 * ```
 */
interface Group : Object

/**
 * Represents an organization.
 *
 * Example:
 * ```json
 * {
 *  "@context": "https://www.w3.org/ns/activitystreams",
 *  "type": "Organization",
 *  "name": "Example Co."
 * }
 */
interface Organization : Object

/**
 * Represents an individual person.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Person",
 *   "name": "Sally Smith"
 * }
 * ```
 */
interface Person : Object

/**
 * Represents a service of any kind.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Service",
 *   "name": "Acme Web Service"
 * }
 * ```
 */
interface Service : Object