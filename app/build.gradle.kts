plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Uncomment when adding Hilt:
    // id("com.google.dagger.hilt.android")
    // id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.digitalmonk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.digitalmonk"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        buildConfig = true   // Allows BuildConfig.DEBUG checks
    }
}

dependencies {

    // ── Core ────────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // ── Compose ─────────────────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // ── Navigation ───────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // ── Room (local database for app rules, usage logs) ──────────────────────
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    // annotationProcessor("androidx.room:room-compiler:2.6.1")  // use KSP instead:
    // ksp("androidx.room:room-compiler:2.6.1")

    // ── WorkManager (background tasks, blocklist sync) ───────────────────────
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ── Secure Storage (for PIN, premium state) ──────────────────────────────
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ── DataStore (modern prefs — use for new features) ──────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Hilt (dependency injection — add when ready) ─────────────────────────
    // implementation("com.google.dagger:hilt-android:2.51")
    // ksp("com.google.dagger:hilt-android-compiler:2.51")
    // implementation("androidx.hilt:hilt-work:1.2.0")
    // implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── Google Play Billing (subscriptions) ──────────────────────────────────
    // implementation("com.android.billingclient:billing-ktx:7.0.0")

    // ── Charts (usage reports) ────────────────────────────────────────────────
    // implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")

    // ── Gson (kept for compatibility) ─────────────────────────────────────────
    implementation("com.google.code.gson:gson:2.10.1")

    // ── Testing ──────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}