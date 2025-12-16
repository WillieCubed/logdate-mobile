plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.logdate.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.logdate.wear"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        // Enable core library desugaring for health-connect
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    // Core library desugaring for health-connect
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // Koin dependency injection
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    
    // Napier logging
    implementation(libs.napier)
    
    // Kotlinx coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Minimal client dependencies for Wear - avoid full feature modules that include Material3
    // implementation(projects.client.repository)
    // implementation(projects.client.feature.editor)
    
    // Add navigation for Wear
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.iconsExtended)
    
    // Additional Compose support
    implementation(libs.kotlinx.uuid.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.play.services.wearable)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    
    // Material 3 for Wear OS
    implementation(libs.androidx.wear.material3)
    implementation(libs.androidx.wear.foundation)
    implementation(libs.androidx.wear.navigation)
    
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.tiles)
    implementation(libs.androidx.tiles.material)
    implementation(libs.androidx.tiles.tooling.preview)
    implementation(libs.horologist.compose.tools)
    implementation(libs.horologist.tiles)
    implementation(libs.androidx.watchface.complications.data.source.ktx)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.tiles.tooling)
}