package app.logdate.core.billing

import app.logdate.core.billing.model.BackupPlanOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

// TODO: Move to debug build variant, remove from release
/**
 * A fake implementation of [SubscriptionBiller] for testing purposes.
 */
class DemoSubscriptionBiller @Inject constructor() : SubscriptionBiller {

    private var currentPlan: BackupPlanOption = BackupPlanOption.BASIC

    override suspend fun purchasePlan(plan: BackupPlanOption) {
        currentPlan = plan
    }

    override suspend fun cancelPlan() {
        currentPlan = BackupPlanOption.BASIC
    }

    override val isSubscribed: Flow<Boolean>
        get() = flow { emit(currentPlan != BackupPlanOption.BASIC) }

    override val availablePlans: Flow<List<BackupPlanOption>> = flow {
        emit(
            listOf(
                BackupPlanOption.BASIC,
                BackupPlanOption.STANDARD,
            )
        )
    }
}