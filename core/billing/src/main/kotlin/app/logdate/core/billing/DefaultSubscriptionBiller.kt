package app.logdate.core.billing

import android.content.Context
import app.logdate.core.billing.model.BackupPlanOption
import app.logdate.core.coroutines.AppDispatcher
import app.logdate.core.coroutines.Dispatcher
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DefaultSubscriptionBiller @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : SubscriptionBiller {

    override val isSubscribed: Flow<Boolean>
        get() = TODO("Not yet implemented")

    override val availablePlans: Flow<List<BackupPlanOption>>
        get() = flow {
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId("logdate_backup_plan_basic")
                    .setProductType(BillingClient.ProductType.SUBS).build()
            )
            val params = QueryProductDetailsParams.newBuilder()
            params.setProductList(productList)

            withContext(ioDispatcher) {
                val details = billingClient.queryProductDetails(params.build())

            }
        }

    override suspend fun purchasePlan(plan: BackupPlanOption) = withContext(ioDispatcher) {
//        billingClient.launchBillingFlow(context as Activity, billingFlowParams)
    }

    override suspend fun cancelPlan() {
        TODO("Not yet implemented")
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        // To be implemented in a later section.
    }

    private val billingClient =
        BillingClient.newBuilder(context).setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            ).build()

}