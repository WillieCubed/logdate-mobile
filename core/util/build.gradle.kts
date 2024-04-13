plugins {
    alias(libs.plugins.logdate.jvm.library)
    alias(libs.plugins.logdate.documentation)
}

dependencies {
    implementation(libs.kotlinx.datetime)
}
