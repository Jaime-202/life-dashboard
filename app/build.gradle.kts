import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// ─── Leer la API key de local.properties (nunca en código fuente) ─────────────
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.lifedashboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lifedashboard"
        minSdk = 28          // Health Connect requiere API 28+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // La clave se inyecta en BuildConfig en tiempo de compilación
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProps.getProperty("GEMINI_API_KEY", "")}\""
        )
    }


    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ─── Core Android ───────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("com.google.android.material:material:1.12.0")

    // ─── Jetpack Compose ────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // ─── Navigation Compose ─────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ─── Lifecycle & ViewModel ──────────────────────────────────────
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ─── Hilt (DI) ──────────────────────────────────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // ─── Room (Local DB) ────────────────────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ─── WorkManager ────────────────────────────────────────────────
    implementation(libs.androidx.work.runtime.ktx)

    // ─── Health Connect ─────────────────────────────────────────────
    implementation(libs.androidx.health.connect.client)

    // ─── Jetpack Glance (Widget) ─────────────────────────────────────
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // ─── DataStore (Preferencias) ────────────────────────────────────
    implementation(libs.androidx.datastore.preferences)

    // ─── Gemini AI SDK ───────────────────────────────────────────────
    implementation(libs.generativeai)

    // ─── Kotlin Coroutines ──────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ─── Kotlin Serialization ───────────────────────────────────────
    implementation(libs.kotlinx.serialization.json)

    // ─── Splash Screen ──────────────────────────────────────────────
    implementation(libs.androidx.core.splashscreen)

    // ─── Accompanist (permisos runtime) ─────────────────────────────
    implementation(libs.accompanist.permissions)

    // ─── DocumentFile (SAF / Symmetry Sync) ─────────────────────────
    implementation("androidx.documentfile:documentfile:1.0.1")

    // ─── Testing ────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
