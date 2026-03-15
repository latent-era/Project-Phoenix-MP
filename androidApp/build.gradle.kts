plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val injectedVersionCode = providers.gradleProperty("version.code").orNull?.let { raw ->
    val parsed = raw.toIntOrNull()
        ?: throw GradleException(
            "Invalid -Pversion.code='$raw'. Expected an integer between 1 and 2100000000."
        )

    if (parsed !in 1..2_100_000_000) {
        throw GradleException(
            "Invalid -Pversion.code='$raw'. Expected an integer between 1 and 2100000000."
        )
    }

    parsed
}

android {
    namespace = "com.devil.phoenixproject"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.devil.phoenixproject"
        minSdk = 26
        targetSdk = 36
        // Fail fast if CI injects an invalid version code instead of silently shipping versionCode=3.
        versionCode = injectedVersionCode ?: 3
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            val keystorePath = file("${System.getProperty("user.home")}/.android/debug.keystore")
            if (keystorePath.exists()) {
                storeFile = keystorePath
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    // Shared module
    implementation(project(":shared"))
    
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.navigation)

    // Compose Tooling
    debugImplementation(libs.compose.ui.tooling)

    // Koin DI
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Logging
    implementation(libs.kermit)

    // RevenueCat (needed for SubscriptionManager interface visibility)
    implementation(libs.revenuecat.purchases.core)

    // Image Loading - Coil 3
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)
    implementation(libs.ktor.client.okhttp)

    // Testing - Unit Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)

    // Testing - Instrumented/E2E Tests
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.koin.test)
    androidTestImplementation(libs.koin.test.junit4)
    androidTestImplementation(libs.multiplatform.settings.test)
    androidTestImplementation(libs.multiplatform.settings)
    debugImplementation(libs.compose.ui.test.manifest)
}
