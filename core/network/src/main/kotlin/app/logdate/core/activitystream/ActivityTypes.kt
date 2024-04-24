package app.logdate.core.activitystream

/**
 * Indicates that the actor accepts the object. The target property can be used in certain
 * circumstances to indicate the context into which the object has been accepted.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally accepted an invitation to a party",
 *   "type": "Accept",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Invite",
 *     "actor": "http://john.example.org",
 *     "object": {
 *       "type": "Event",
 *       "name": "Going-Away Party for Jim"
 *     }
 *   }
 * }
 * ```
 */
interface Accept : Activity

/**
 * A specialization of [Accept] indicating that the acceptance is tentative.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally tentatively accepted an invitation to a party",
 *   "type": "TentativeAccept",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Invite",
 *     "actor": "http://john.example.org",
 *     "object": {
 *       "type": "Event",
 *       "name": "Going-Away Party for Jim"
 *     }
 *   }
 * }
 * ```
 */
interface TentativeAccept : Activity

/**
 * Indicates that the actor has added the object to the target. If the target property is not
 * explicitly specified, the target would need to be determined implicitly by context. The origin can be used to identify the context from which the object originated.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally added an object",
 *   "type": "Add",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": "http://example.org/abc"
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally added a picture of her cat to her cat picture collection",
 *   "type": "Add",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Image",
 *     "name": "A picture of my cat",
 *     "url": "http://example.org/img/cat.png"
 *   },
 *   "origin": {
 *     "type": "Collection",
 *     "name": "Camera Roll"
 *   },
 *   "target": {
 *     "type": "Collection",
 *     "name": "My Cat Pictures"
 *   }
 * }
 * ```
 */
interface Add : Activity

/**
 * An [IntransitiveActivity] that indicates that the actor has arrived at the location. The origin
 * can be used to identify the context from which the actor originated. The target typically has no
 * defined meaning.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally arrived at work",
 *   "type": "Arrive",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "location": {
 *     "type": "Place",
 *     "name": "Work"
 *   },
 *   "origin": {
 *     "type": "Place",
 *     "name": "Home"
 *   }
 * }
 * ```
 */
interface Arrive : IntransitiveActivity

/**
 * Indicates that the `actor` has created the `object`.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally created a note",
 *   "type": "Create",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Note",
 *     "name": "A Simple Note",
 *     "content": "This is a simple note"
 *   }
 * }
 * ```
 */
interface Create : Activity

/**
 * Indicates that the `actor` has deleted the `object`. If specified, the `origin` indicates the
 * context from which the `object` was deleted.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally deleted a note",
 *   "type": "Delete",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": "http://example.org/notes/1",
 *   "origin": {
 *     "type": "Collection",
 *     "name": "Sally's Notes"
 *   }
 * }
 * ```
 */
interface Delete : Activity

/**
 * Indicates that the `actor` is "following" the `object`. Following is defined in the sense
 * typically used within Social systems in which the actor is interested in any activity performed
 * by or on the object. The `target` and `origin` typically have no defined meaning.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally followed John",
 *   "type": "Follow",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Person",
 *     "name": "John"
 *   }
 * }
 * ```
 */
interface Follow : Activity

/**
 * Indicates that the `actor` is ignoring the `object`. The `target` and `origin` typically have no
 * defined meaning.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally ignored a note",
 *   "type": "Ignore",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": "http://example.org/notes/1"
 * }
 * ```
 */
interface Ignore : Activity

/**
 * Indicates that the `actor` has joined the `object`. The `target` and `origin` typically have no
 * defined meaning.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally joined a group",
 *   "type": "Join",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Group",
 *     "name": "A Simple Group"
 *   }
 * }
 * ```
 */
interface Join : Activity

/**
 * Indicates that the `actor` has left the `object`. The `target` and `origin` typically have no
 * meaning.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally left work",
 *   "type": "Leave",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Place",
 *     "name": "Work"
 *   }
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally left a group",
 *   "type": "Leave",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Group",
 *     "name": "A Simple Group"
 *   }
 * }
 * ```
 */
interface Leave : Activity

/**
 * Indicates that the `actor` likes, recommends or endorses the `object`. The `target` and `origin`
 * typically have no defined meaning.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally liked a note",
 *   "type": "Like",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": "http://example.org/notes/1"
 * }
 * ```
 */
interface Like : Activity

/**
 * Indicates that the `actor` is offering the `object`. If specified, the `target` indicates the
 * entity to which the `object` is being offered.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally offered 50% off to Lewis",
 *   "type": "Offer",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "http://www.types.example/ProductOffer",
 *     "name": "50% Off!"
 *   },
 *   "target": {
 *     "type": "Person",
 *     "name": "Lewis"
 *   }
 * }
 * ```
 */
interface Offer : Activity

/**
 * A specialization of [Offer] in which the `actor` is extending an invitation for the `object` to
 * the `target`.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally invited John and Lisa to a party",
 *   "type": "Invite",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Event",
 *     "name": "A Party"
 *   },
 *   "target": [
 *     {
 *       "type": "Person",
 *       "name": "John"
 *     },
 *     {
 *       "type": "Person",
 *       "name": "Lisa"
 *     }
 *   ]
 * }
 * ```
 */
interface Invite : Offer

/**
 * Indicates that the `actor` is rejecting the `object`. The `target` and `origin` typically have no
 * defined meaning.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally rejected an invitation to a party",
 *   "type": "Reject",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Invite",
 *     "actor": "http://john.example.org",
 *     "object": {
 *       "type": "Event",
 *       "name": "Going-Away Party for Jim"
 *     }
 *   }
 * }
 * ```
 */
interface Reject : Activity

/**
 * A specialization of [Reject] in which the rejection is considered tentative.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally tentatively rejected an invitation to a party",
 *   "type": "TentativeReject",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Invite",
 *     "actor": "http://john.example.org",
 *     "object": {
 *       "type": "Event",
 *       "name": "Going-Away Party for Jim"
 *     }
 *   }
 * }
 * ```
 */
interface TentativeReject : Reject

/**
 * 	Indicates that the `actor` is removing the `object`. If specified, the `origin` indicates the
 * 	context from which the `object` is being removed.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally removed a note from her notes folder",
 *   "type": "Remove",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": "http://example.org/notes/1",
 *   "origin": {
 *     "type": "Collection",
 *     "name": "Notes Folder"
 *   }
 * }
 * ```
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "The moderator removed Sally from a group",
 *   "type": "Remove",
 *   "actor": {
 *     "type": "http://example.org/Role",
 *     "name": "The Moderator"
 *   },
 *   "object": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "origin": {
 *     "type": "Group",
 *     "name": "A Simple Group"
 *   }
 * }
 * ```
 */
interface Remove : Activity

/**
 * Indicates that the `actor` is undoing the `object`. In most cases, the `object` will be an
 * [Activity] describing some previously performed action (for instance, a person may have
 * previously "liked" an article but, for whatever reason, might choose to undo that like at some
 * later point in time).
 *
 * The `target` and `origin` typically have no defined meaning.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally retracted her offer to John",
 *   "type": "Undo",
 *   "actor": "http://sally.example.org",
 *   "object": {
 *     "type": "Offer",
 *     "actor": "http://sally.example.org",
 *     "object": "http://example.org/posts/1",
 *     "target": "http://john.example.org"
 *   }
 * }
 * ```
 */
interface Undo : Activity

/**
 * Indicates that the `actor` has updated the `object`. Note, however, that this vocabulary does not
 * define a mechanism for describing the actual set of modifications made to `object`.
 *
 * The target and origin typically have no defined meaning.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally updated her note",
 *   "type": "Update",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": "http://example.org/notes/1"
 * }
 * ```
 */
interface Update : Activity

/**
 * Indicates that the `actor` viewed the `object`.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally read an article",
 *   "type": "View",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Article",
 *     "name": "What You Should Know About Activity Streams"
 *   }
 * }
 * ```
 */
interface View : Activity

/**
 * Indicates that the `actor` listened to the `object`.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally listened to a piece of music",
 *   "type": "Listen",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": "http://example.org/music.mp3"
 * }
 * ```
 */
interface Listen : Activity

/**
 * Indicates that the `actor` read the `object`.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally read a blog post",
 *   "type": "Read",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": "http://example.org/posts/1"
 * }
 * ```
 */
interface Read : Activity

/**
 * Indicates that the `actor` has moved object from `origin` to `target`. If the `origin` or `target`
 * are not specified, either can be determined by context.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally moved a post from List A to List B",
 *   "type": "Move",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "object": "http://example.org/posts/1",
 *   "target": {
 *     "type": "Collection",
 *     "name": "List B"
 *   },
 *   "origin": {
 *     "type": "Collection",
 *     "name": "List A"
 *   }
 * }
 * ```
 */
interface Move : Activity

/**
 * Indicates that the `actor` is traveling to `target` from `origin`. Travel is an
 * [IntransitiveActivity] whose `actor` specifies the direct object. If the `target` or `origin` are
 * not specified, either can be determined by context.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally went home from work",
 *   "type": "Travel",
 *   "actor": {
 *     "type": "Person",
 *     "name": "Sally"
 *   },
 *   "target": {
 *     "type": "Place",
 *     "name": "Home"
 *   },
 *   "origin": {
 *     "type": "Place",
 *     "name": "Work"
 *   }
 * }
 * ```
 */
interface Travel : IntransitiveActivity

/**
 * Indicates that the `actor` is calling the `target`'s attention the `object`.
 *
 * The `origin` typically has no defined meaning.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally announced that she had arrived at work",
 *   "type": "Announce",
 *   "actor": {
 *     "type": "Person",
 *     "id": "http://sally.example.org",
 *     "name": "Sally"
 *   },
 *   "object": {
 *     "type": "Arrive",
 *     "actor": "http://sally.example.org",
 *     "location": {
 *       "type": "Place",
 *       "name": "Work"
 *     }
 *   }
 * }
 */
interface Announce : Activity

/**
 * Indicates that the `actor` is blocking the `object`. Blocking is a stronger form of [Ignore]. The
 * typical use is to support social systems that allow one user to block activities or content of
 * other users. The `target` and `origin` typically have no defined meaning.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally blocked Joe",
 *   "type": "Block",
 *   "actor": "http://sally.example.org",
 *   "object": "http://joe.example.org"
 * }
 * ```
 */
interface Block : Ignore

/**
 * Indicates that the `actor` is "flagging" the `object`. Flagging is defined in the sense common to
 * many social platforms as reporting content as being inappropriate for any number of reasons.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally flagged an inappropriate note",
 *   "type": "Flag",
 *   "actor": "http://sally.example.org",
 *   "object": {
 *     "type": "Note",
 *     "content": "An inappropriate note"
 *   }
 * }
 * ```
 */
interface Flag : Activity

/**
 * Indicates that the `actor` dislikes the `object`.
 *
 * Example:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "summary": "Sally disliked a post",
 *   "type": "Dislike",
 *   "actor": "http://sally.example.org",
 *   "object": "http://example.org/posts/1"
 * }
 * ```
 */
interface Dislike : Activity

/**
 * Represents a question being asked. Question objects are an extension of [IntransitiveActivity].
 * That is, the Question object is an Activity, but the direct object is the question itself and
 * therefore it would not contain an `object` property.
 *
 * Either of the `anyOf` and `oneOf` properties may be used to express possible answers, but a
 * Question object must not have both properties.
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
 *
 * Example 2:
 * ```json
 * {
 *   "@context": "https://www.w3.org/ns/activitystreams",
 *   "type": "Question",
 *   "name": "What is the answer?",
 *   "closed": "2016-05-10T00:00:00Z"
 * }
 * ```
 */
sealed interface Question : IntransitiveActivity {
    val closed: Closed<*>

    interface MultipleChoiceQuestion : Question {
        val anyOf: List<Object>
    }

    interface Poll : Question {
        val oneOf: List<Object>
    }
}

