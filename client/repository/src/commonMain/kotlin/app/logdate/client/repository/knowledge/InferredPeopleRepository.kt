package app.logdate.client.repository.knowledge

import app.logdate.shared.model.InferredPersonCluster
import app.logdate.shared.model.InferredPersonEvidence
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface InferredPeopleRepository {
    fun observeOpenClusters(): Flow<List<InferredPersonCluster>>

    fun observeEvidence(clusterId: Uuid): Flow<List<InferredPersonEvidence>>

    suspend fun refresh()

    suspend fun confirmClusterAsPerson(clusterId: Uuid)

    suspend fun rejectCluster(clusterId: Uuid)
}
