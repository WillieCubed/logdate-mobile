package app.logdate.lexicon

import studio.hypertext.atproto.lexicon.LexiconParser
import studio.hypertext.atproto.lexicon.LexiconRecordValidator
import studio.hypertext.atproto.lexicon.LexiconRegistry

/**
 * LogDate's lexicon schemas, available at runtime on every platform.
 *
 * The schemas are built into the app from [LogDateSchemas], so a record that arrives from another
 * device can be checked against its schema before anything stores it.
 */
public object LogDateLexicons {
    /** Every LogDate and community schema, parsed once on first use. */
    public val registry: LexiconRegistry by lazy { LexiconRegistry(LogDateSchemas.documents.map(LexiconParser::parse)) }

    /** Checks records against [registry]. */
    public val validator: LexiconRecordValidator by lazy { LexiconRecordValidator(registry) }
}
