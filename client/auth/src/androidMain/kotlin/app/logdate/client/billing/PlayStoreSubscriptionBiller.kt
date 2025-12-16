package app.logdate.client.billing

import android.content.Context
import app.logdate.client.billing.model.LogDateBackupPlanOption
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlayStoreSubscriptionBiller @Inject constructor(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SubscriptionBiller {

    override val isSubscribed: Flow<Boolean>
        get() = TODO("Not yet implemented")

    override val availablePlans: Flow<List<LogDateBackupPlanOption>>
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

    override suspend fun purchasePlan(plan: LogDateBackupPlanOption) = withContext(ioDispatcher) {
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