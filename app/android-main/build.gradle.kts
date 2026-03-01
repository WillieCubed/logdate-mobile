import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.googleServices)
}

extensions.configure<ApplicationExtension> {
    namespace = "app.logdate.client"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "co.reasonabletech.logdate"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isDebuggable = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "**/*.properties"
            pickFirsts += "META-INF/DEPENDENCIES"
            pickFirsts += "META-INF/LICENSE"
            pickFirsts += "META-INF/LICENSE.txt"
            pickFirsts += "META-INF/LICENSE.md"
            pickFirsts += "META-INF/NOTICE"
            pickFirsts += "META-INF/NOTICE.txt"
            pickFirsts += "META-INF/INDEX.LIST"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(projects.app.composeMain)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
