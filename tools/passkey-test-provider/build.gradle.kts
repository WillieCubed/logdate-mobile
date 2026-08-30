plugins {
    alias(libs.plugins.android.application)
}

/**
 * Emulator-only WebAuthn credential provider.
 *
 * Google Password Manager refuses to create passkeys unless a Google account is signed in, so a
 * bare emulator can only offer cross-device (QR) creation — which needs a real phone. This app
 * registers a real [androidx.credentials.provider.CredentialProviderService] that performs genuine
 * P-256 WebAuthn ceremonies locally, which lets managed devices and emulators exercise the full
 * Credential Manager path.
 *
 * It is a TEST AUTHENTICATOR: user verification is asserted, not performed. Never ship it.
 */
android {
    namespace = "app.logdate.tools.passkeyprovider"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.logdate.tools.passkeyprovider"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.core.ktx)

}
