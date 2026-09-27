plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied anywhere: AGP 9 compiles Kotlin itself. Declaring it pins the Kotlin
    // Gradle plugin on the build classpath to the same version as the Compose compiler.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
