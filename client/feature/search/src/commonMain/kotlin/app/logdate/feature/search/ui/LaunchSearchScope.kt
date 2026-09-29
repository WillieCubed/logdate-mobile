package app.logdate.feature.search.ui

import app.logdate.client.repository.search.SearchContentType

/** Content with a supported destination in the first public Android release. */
internal val launchSearchContentTypes =
    setOf(
        SearchContentType.TEXT_NOTE,
        SearchContentType.TRANSCRIPTION,
        SearchContentType.JOURNAL,
        SearchContentType.MEDIA_CAPTION,
        SearchContentType.REWIND,
    )
