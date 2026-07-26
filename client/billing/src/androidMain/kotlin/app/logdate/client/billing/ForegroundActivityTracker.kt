package app.logdate.client.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle

/**
 * Keeps a reference to whichever [Activity] is currently in the foreground.
 *
 * Play Billing has to be launched from a real Activity. The billing module is constructed with the
 * application context, which can never be unwrapped into one, so a purchase attempt could not
 * reach the Play sheet at all. Tracking resume and pause gives the biller something concrete to
 * launch from without the billing module having to reach into the UI layer.
 *
 * The reference is cleared on pause so a backgrounded or destroyed Activity is never handed to
 * Play Billing, and so it cannot be leaked.
 */
internal class ForegroundActivityTracker(
    application: Application,
) : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var foregroundActivity: Activity? = null

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    /** The foreground Activity, or `null` when nothing is resumed. */
    fun current(): Activity? = foregroundActivity

    override fun onActivityResumed(activity: Activity) {
        foregroundActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {
        if (foregroundActivity === activity) {
            foregroundActivity = null
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (foregroundActivity === activity) {
            foregroundActivity = null
        }
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit
}

/**
 * Builds a tracker when [context] belongs to an [Application], which is how the billing module is
 * constructed. Returns `null` otherwise, in which case the caller unwraps the context directly.
 */
internal fun trackerFor(context: Context): ForegroundActivityTracker? =
    when (val applicationContext = context.applicationContext) {
        is Application -> ForegroundActivityTracker(applicationContext)
        else -> null
    }

internal tailrec fun Context.unwrapActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.unwrapActivity()
        else -> null
    }
