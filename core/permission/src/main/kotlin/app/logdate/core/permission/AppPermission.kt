package app.logdate.core.permission

/**
 * TODO: Make multiplatform
 */
enum class AppPermission(
    val value: String,
) {
    PRECISE_LOCATION("android.permission.ACCESS_FINE_LOCATION"),
    COARSE_LOCATION("android.permission.ACCESS_COARSE_LOCATION"),
}