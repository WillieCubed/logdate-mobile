package app.logdate.shared.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * What a user typed back at one of the rewind's noticing prompts.
 *
 * The point of this type is recognition over analysis: when someone re-opens an old
 * rewind, they should see the same noticing they replied to before alongside the words
 * they wrote. The [observation] and [invitation] are denormalized into the response so
 * that even if the rewind is later regenerated and the AI invents a different prompt,
 * the user can still see what they were originally responding to.
 *
 * @property rewindId The rewind this response belongs to.
 * @property key Stable content-derived identifier for the prompt — see [ReflectionPromptKey].
 * @property observation The factual line the prompt grounded itself in, as the user saw it.
 * @property invitation The open question the user was answering.
 * @property responseText The user's reply, exactly as they typed it.
 * @property created When the user first wrote a response to this prompt.
 * @property lastUpdated When the response was most recently edited.
 */
data class ReflectionPromptResponse(
    val rewindId: Uuid,
    val key: ReflectionPromptKey,
    val observation: String,
    val invitation: String,
    val responseText: String,
    val created: Instant,
    val lastUpdated: Instant,
)
