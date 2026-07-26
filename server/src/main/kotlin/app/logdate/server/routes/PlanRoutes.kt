package app.logdate.server.routes

import app.logdate.server.entitlements.PlanCatalogService
import app.logdate.shared.model.PlanCatalogResponse
import io.github.smiley4.ktoropenapi.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

/**
 * Publishes the plans this deployment offers.
 *
 * Deliberately unauthenticated: the point is to show what an account would get *before* someone
 * has one. A deployment with billing switched off answers with an empty list rather than an error,
 * which clients read as "this server does not sell anything" and hide plan selection accordingly.
 */
fun Route.planRoutes(planCatalogService: PlanCatalogService) {
    get("/plans", {}) {
        call.respond(
            PlanCatalogResponse(
                success = true,
                data = planCatalogService.plans(),
            ),
        )
    }
}
