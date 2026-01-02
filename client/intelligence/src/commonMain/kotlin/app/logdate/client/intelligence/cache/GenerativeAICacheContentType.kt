package app.logdate.client.intelligence.cache

sealed interface GenerativeAICacheContentType {
    val id: String

    data object Summary : GenerativeAICacheContentType {
        override val id: String = "summary"
    }

    data object People : GenerativeAICacheContentType {
        override val id: String = "people"
    }

    data object Narrative : GenerativeAICacheContentType {
        override val id: String = "narrative"
    }

    data object Onboarding : GenerativeAICacheContentType {
        override val id: String = "onboarding"
    }

    companion object {
        fun fromId(id: String): GenerativeAICacheContentType? = when (id) {
            Summary.id -> Summary
            People.id -> People
            Narrative.id -> Narrative
            Onboarding.id -> Onboarding
            else -> null
        }
    }
}
