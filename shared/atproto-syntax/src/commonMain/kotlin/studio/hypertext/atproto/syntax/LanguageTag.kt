package studio.hypertext.atproto.syntax

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

private val languageTagRegex: Regex = Regex("^[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*$")

/**
 * A language in the AT Protocol's `language` form: a BCP 47 tag such as `en`, `pt-BR` or
 * `zh-Hant-TW`. Only the tag's shape is checked, not whether each subtag is registered.
 *
 * @property value Raw language tag.
 */
@JvmInline
@Serializable
public value class LanguageTag(
    public val value: String,
) {
    init {
        if (!languageTagRegex.matches(value)) throw InvalidLanguageTagException(value)
    }

    /**
     * Returns the raw language tag.
     */
    override fun toString(): String = value

    /**
     * Factory helpers for [LanguageTag].
     */
    public companion object {
        /**
         * Parses [value] into a [LanguageTag], returning failures as [Result].
         */
        public fun parse(value: String): Result<LanguageTag> = runCatching { LanguageTag(value) }

        /**
         * Parses [value] into a [LanguageTag] or throws [InvalidLanguageTagException].
         */
        public fun require(value: String): LanguageTag = LanguageTag(value)

        /**
         * Returns `true` when [value] can be parsed as a [LanguageTag].
         */
        public fun isValid(value: String): Boolean = languageTagRegex.matches(value)
    }
}
