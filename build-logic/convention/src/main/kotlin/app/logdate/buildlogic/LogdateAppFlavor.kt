package app.logdate.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ProductFlavor

@Suppress("EnumEntryName")
enum class FlavorDimension {
    contentType
}

enum class LogdateAppFlavor(
    val dimension: FlavorDimension,
    val applicationIdSuffix: String? = null,
) {
    /**
     * A demo version that can be used for testing without real data.
     */
    DEMO(FlavorDimension.contentType, applicationIdSuffix = ".demo"),

    /**
     * The production version of the app that uses LogDate Cloud by default.
     */
    PROD(FlavorDimension.contentType)
}

fun configureFlavors(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
    flavorConfigurationBlock: ProductFlavor.(flavor: LogdateAppFlavor) -> Unit = {}
) {
    commonExtension.apply {
        flavorDimensions += FlavorDimension.contentType.name
        productFlavors {
            LogdateAppFlavor.values().forEach {
                create(it.name.lowercase()) {
                    dimension = it.dimension.name
                    flavorConfigurationBlock(this, it)
                    if (this@apply is ApplicationExtension && this is ApplicationProductFlavor) {
                        if (it.applicationIdSuffix != null) {
                            applicationIdSuffix = it.applicationIdSuffix
                        }
                    }
                }
            }
        }
    }
}