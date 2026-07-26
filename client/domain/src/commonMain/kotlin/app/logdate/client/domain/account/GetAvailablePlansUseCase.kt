package app.logdate.client.domain.account

import app.logdate.client.networking.PlanCatalogClient
import app.logdate.shared.model.PlanOption

/**
 * Reads the plans the currently selected server offers.
 *
 * Answers against whichever server is configured, so someone pointed at their own deployment sees
 * that server's terms rather than LogDate Cloud's.
 */
class GetAvailablePlansUseCase(
    private val planCatalogClient: PlanCatalogClient,
) {
    /**
     * Outcome of reading the catalog.
     *
     * [Unavailable] is deliberately distinct from [Failed]: a server that offers nothing is a
     * normal, successful answer that should hide plan selection, whereas a failure means the
     * catalog is unknown and worth retrying.
     */
    sealed interface Result {
        data class Available(
            val plans: List<PlanOption>,
        ) : Result

        data object Unavailable : Result

        data object Failed : Result
    }

    suspend operator fun invoke(): Result =
        planCatalogClient
            .fetchPlans()
            .fold(
                onSuccess = { plans ->
                    if (plans.isEmpty()) Result.Unavailable else Result.Available(plans)
                },
                onFailure = { Result.Failed },
            )
}
