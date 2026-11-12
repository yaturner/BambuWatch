import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing with the Play upload key, read from the gitignored keystore.properties
// (see README). Without that file, release builds are left unsigned.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val appVersionCode = (property("appVersionCode") as String).toInt()
val appVersionName = property("appVersionName") as String

android {
    namespace = "com.das.bambuwatch.wear"
    compileSdk = 36

    defaultConfig {
        // Must equal the phone app's applicationId (and be signed with the same key)
        // or the Wearable Data Layer won't connect them.
        applicationId = "com.das.printwatcher"
        minSdk = 30 // Wear OS 3 (Galaxy Watch4 and later)
        targetSdk = 36
        versionCode = appVersionCode * 10 + 1
        versionName = appVersionName
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.androidx.wear.complications.data.source.ktx)
    implementation(libs.androidx.wear.ongoing)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
