package studio.hypertext.atproto.lexicon

import studio.hypertext.atproto.syntax.Nsid

/**
 * In-memory registry of parsed lexicon documents.
 */
public class LexiconRegistry(
    documents: Iterable<LexiconDocument> = emptyList(),
) {
    private val documentsById: MutableMap<Nsid, LexiconDocument> = linkedMapOf()

    init {
        documents.forEach(::register)
    }

    /**
     * Registers [document], replacing any previous document with the same id.
     */
    public fun register(document: LexiconDocument) {
        documentsById[document.id] = document
    }

    /**
     * Returns the document with [id], if present.
     */
    public fun document(id: Nsid): LexiconDocument? = documentsById[id]

    /**
     * Resolves [reference] to a definition, if present.
     */
    public fun resolve(reference: LexiconReference): LexiconDefinition? =
        documentsById[reference.documentId]?.definitions?.get(reference.definitionName)

    /**
     * Returns all registered documents in registration order.
     */
    public fun documents(): List<LexiconDocument> = documentsById.values.toList()
}
