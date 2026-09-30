package app.logdate.client.domain.location.history

import app.logdate.client.location.places.ExternalPlacesProvider
import app.logdate.client.location.places.PlaceSuggestion
import app.logdate.shared.model.Location
import app.logdate.shared.model.location.SemanticPlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

class SuggestNearbyHistoryPlacesUseCaseTest {
    @Test
    fun `adjacent businesses have distinct opaque identities and stay unconfirmed`() =
        runTest {
            val useCase = SuggestNearbyHistoryPlacesUseCase(provider(listOf(suggestion("cafe"), suggestion("bookshop"))))
            val first = useCase(36.1, -115.1, emptyList())
            assertEquals(2, first.size)
            assertNotEquals(first[0].id, first[1].id)
            first.forEach {
                assertNotEquals(it.externalId, it.id)
                Uuid.parse(it.id)
                assertFalse(it.userConfirmed)
            }
            assertEquals(first.map { it.id }, useCase(36.1, -115.1, emptyList()).map { it.id })
        }

    @Test
    fun `only exact external identity reuses an existing place`() =
        runTest {
            val saved = SemanticPlace(Uuid.random().toString(), "My favorite cafe", 36.1, -115.1, externalId = "cafe", userConfirmed = true)
            val useCase = SuggestNearbyHistoryPlacesUseCase(provider(listOf(suggestion("cafe"), suggestion("bookshop"))))
            val candidates = useCase(36.1, -115.1, listOf(saved))
            assertEquals(saved.id, candidates.first().id)
            assertEquals(saved.name, candidates.first().name)
            assertFalse(candidates.first().userConfirmed)
            assertNotEquals(saved.id, candidates.last().id)
        }

    @Test
    fun `invalid or duplicate provider records do not become alternatives`() =
        runTest {
            val useCase =
                SuggestNearbyHistoryPlacesUseCase(
                    provider(
                        listOf(
                            suggestion("cafe"),
                            suggestion("cafe"),
                            suggestion(null),
                            suggestion("bad").copy(latitude = Double.NaN),
                            suggestion("blank").copy(name = " "),
                        ),
                    ),
                )
            assertEquals(listOf("cafe"), useCase(36.1, -115.1, emptyList()).map { it.externalId })
        }

    @Test
    fun `provider failure leaves suggestions empty without replacing saved places`() =
        runTest {
            val useCase =
                SuggestNearbyHistoryPlacesUseCase(
                    object : ExternalPlacesProvider {
                        override suspend fun searchNearbyPlaces(location: Location): List<PlaceSuggestion> = error("Unavailable")
                    },
                )
            assertEquals(emptyList(), useCase(36.1, -115.1, emptyList()))
        }

    @Test
    fun `cancellation is propagated instead of converted to no suggestions`() =
        runTest {
            val useCase =
                SuggestNearbyHistoryPlacesUseCase(
                    object : ExternalPlacesProvider {
                        override suspend fun searchNearbyPlaces(location: Location): List<PlaceSuggestion> = throw CancellationException()
                    },
                )
            assertFailsWith<CancellationException> { useCase(36.1, -115.1, emptyList()) }
        }

    private fun provider(suggestions: List<PlaceSuggestion>) =
        object : ExternalPlacesProvider {
            override suspend fun searchNearbyPlaces(location: Location) = suggestions
        }

    private fun suggestion(id: String?) = PlaceSuggestion("Business $id", "Arts District", 36.1, -115.1, 80, externalId = id)
}
