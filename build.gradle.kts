// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.compose)      apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.hilt)                apply false
}

val tmpDir = System.getProperty("java.io.tmpdir").replace('\\', '/')
allprojects {
    layout.buildDirectory.set(file("$tmpDir/life_dashboard_build/${project.name}"))
}
