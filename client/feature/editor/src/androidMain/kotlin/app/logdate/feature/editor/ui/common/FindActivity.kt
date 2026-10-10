package app.logdate.feature.editor.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The activity this context belongs to, unwrapping themed and other wrapper contexts, or null. */
internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
