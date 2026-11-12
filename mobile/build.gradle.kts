import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Optional, gitignored: pre-fills debug builds with your printer (see printer.properties.example).
val printerProps = Properties().apply {
    rootProject.file("printer.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun printerProp(key: String): String =
    "\"" + printerProps.getProperty(key, "").trim().replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Release signing with the Play upload key, read from the gitignored keystore.properties
// (see README). Without that file, release builds are left unsigned.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val appVersionCode = (property("appVersionCode") as String).toInt()
val appVersionName = property("appVersionName") as String

android {
    namespace = "com.das.bambuwatch"
    compileSdk = 36

    defaultConfig {
        // Must match the :wear module's applicationId (milestone 4) for the Data Layer to pair them.
        applicationId = "com.das.printwatcher"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode * 10
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

    // play:   Google Play build. No "All files access" (Play policy), so no printer.json settings
    //         file; battery optimization opens the system list instead of the direct prompt.
    // github: sideload build. Keeps Documents/BambuWatch/printer.json and the direct prompt.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "SETTINGS_FILE_ENABLED", "false")
            buildConfigField("boolean", "DIRECT_BATTERY_REQUEST", "false")
        }
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "SETTINGS_FILE_ENABLED", "true")
            buildConfigField("boolean", "DIRECT_BATTERY_REQUEST", "true")
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_PRINTER_IP", printerProp("printer.ip"))
            buildConfigField("String", "DEFAULT_PRINTER_SERIAL", printerProp("printer.serial"))
            buildConfigField("String", "DEFAULT_PRINTER_ACCESS_CODE", printerProp("printer.accessCode"))
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Never bake credentials into a release build.
            buildConfigField("String", "DEFAULT_PRINTER_IP", "\"\"")
            buildConfigField("String", "DEFAULT_PRINTER_SERIAL", "\"\"")
            buildConfigField("String", "DEFAULT_PRINTER_ACCESS_CODE", "\"\"")
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
            // Duplicate Netty metadata pulled in by hivemq-mqtt-client.
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.wearable)
    implementation(libs.androidx.fragment)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.hivemq.mqtt.client)
    // Needed directly for InsecureTrustManagerFactory (HiveMQ declares it as implementation-only).
    implementation(libs.netty.handler)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
