package app.logdate.server.logdate

import java.util.UUID

enum class ResourceKind(
    val pathSegment: String,
) {
    JOURNAL("journal"),
    NOTE("note"),
    REWIND("rewind"),
}

data class ResourceRoute(
    val resourceId: String,
    val accountId: UUID,
    val kind: ResourceKind,
)

interface ResourceRouteRepository {
    suspend fun find(resourceId: String): ResourceRoute?

    suspend fun upsert(route: ResourceRoute): ResourceRoute

    suspend fun delete(resourceId: String)
}

class InMemoryResourceRouteRepository : ResourceRouteRepository {
    private val routes = java.util.concurrent.ConcurrentHashMap<String, ResourceRoute>()

    override suspend fun find(resourceId: String): ResourceRoute? = routes[resourceId]

    override suspend fun upsert(route: ResourceRoute): ResourceRoute {
        routes[route.resourceId] = route
        return route
    }

    override suspend fun delete(resourceId: String) {
        routes.remove(resourceId)
    }
}
