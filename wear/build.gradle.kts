plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.das.bambuwatch.wear"
    compileSdk = 36

    defaultConfig {
        // Must equal the phone app's applicationId (and be signed with the same key)
        // or the Wearable Data Layer won't connect them.
        applicationId = "com.das.bambuwatch"
        minSdk = 30 // Wear OS 3 (Galaxy Watch4 and later)
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.wearable)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
