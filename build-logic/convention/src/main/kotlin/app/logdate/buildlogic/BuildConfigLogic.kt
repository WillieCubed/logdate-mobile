package app.logdate.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project

/**
 * Loads local build properties from the local.properties file and adds them to the build config.
 */
internal fun Project.configureBuildConfig(commonExtension: CommonExtension<*, *, *, *, *, *>) {
    val properties = PropertiesLoader.loadProperties(this)
    commonExtension.apply {
        buildFeatures {
            buildConfig = true
        }

        // Needed to ensure manifest merger doesn't fail
        // See https://developer.android.com/build/manage-manifests#inject_build_variables_into_the_manifest
        defaultConfig {
            manifestPlaceholders["META_APP_ID"] = ""
            manifestPlaceholders["GOOGLE_MAPS_PLACES_API_KEY"] = ""
        }

        buildTypes {
            getByName("debug") {
                buildConfigField(
                    "String",
                    "META_APP_ID",
                    "\"${properties.getProperty("metaAppId")}\""
                )
                buildConfigField(
                    "String",
                    "GOOGLE_MAPS_PLACES_API_KEY",
                    "\"${properties.getProperty("apiKeys.googleMaps")}\""
                )
                buildConfigField(
                    "String",
                    "OPENAI_API_KEY",
                    "\"${properties.getProperty("apiKeys.openai")}\""
                )
                resValue(
                    "string",
                    "GOOGLE_MAPS_PLACES_API_KEY",
                    properties.getProperty("apiKeys.googleMaps")
                )
            }
            // Check if release build type is present
            findByName("release")?.apply {
                buildConfigField(
                    "String",
                    "META_APP_ID",
                    "\"${properties.getProperty("metaAppId")}\""
                )
                buildConfigField(
                    "String",
                    "GOOGLE_MAPS_PLACES_API_KEY",
                    "\"${properties.getProperty("apiKeys.googleMaps")}\""
                )
                buildConfigField(
                    "String",
                    "OPENAI_API_KEY",
                    "\"${properties.getProperty("apiKeys.openai")}\""
                )
                resValue(
                    "string",
                    "GOOGLE_MAPS_PLACES_API_KEY",
                    properties.getProperty("apiKeys.googleMaps")
                )
            }
        }
    }
}