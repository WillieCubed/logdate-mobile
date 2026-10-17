package studio.hypertext.atproto.lexicon

import studio.hypertext.atproto.syntax.Nsid

/**
 * Decides which Kotlin package the code generated for each lexicon document goes in.
 */
public fun interface LexiconPackageLayout {
    /** The package for the document [id]. */
    public fun packageFor(id: Nsid): String

    public companion object {
        /** Every document in the one package [packageName]. */
        public fun flat(packageName: String): LexiconPackageLayout = LexiconPackageLayout { packageName }

        /**
         * Each document in [basePackage] followed by its NSID's namespace, so
         * `app.logdate.journal.entry` goes in `<basePackage>.app.logdate.journal`. Use this to
         * generate a whole schema family, where document names such as `defs` repeat.
         */
        public fun byNamespace(basePackage: String): LexiconPackageLayout =
            LexiconPackageLayout { id ->
                val namespace = id.toString().substringBeforeLast('.').split('.')
                namespace.forEach { segment ->
                    require(segment.matches(identifier) && segment !in kotlinKeywords) {
                        "NSID segment '$segment' of $id can't be a Kotlin package name"
                    }
                }
                (listOf(basePackage) + namespace).joinToString(".")
            }

        private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

internal val kotlinKeywords: Set<String> =
    setOf(
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while",
    )
