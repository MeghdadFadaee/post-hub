import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releasePropertiesFile = rootProject.file("keystore.properties")
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.isFile) {
        releasePropertiesFile.inputStream().use(::load)
    }
}

fun releaseProperty(name: String): String? {
    return releaseProperties.getProperty(name) ?: providers.environmentVariable(name).orNull
}

val releaseStoreFile = releaseProperty("POSTHUB_STORE_FILE")
val releaseStorePassword = releaseProperty("POSTHUB_STORE_PASSWORD")
val releaseKeyAlias = releaseProperty("POSTHUB_KEY_ALIAS")
val releaseKeyPassword = releaseProperty("POSTHUB_KEY_PASSWORD")

android {
    namespace = "net.rodakot.posthub"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "net.rodakot.posthub"
        minSdk = 24
        targetSdk = 36
        versionCode = 102000
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (!releaseStoreFile.isNullOrBlank()) {
                storeFile = rootProject.file(releaseStoreFile)
            }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSigning = signingConfigs.getByName("release")
            if (
                releaseSigning.storeFile != null &&
                releaseSigning.storePassword != null &&
                releaseSigning.keyAlias != null &&
                releaseSigning.keyPassword != null
            ) {
                signingConfig = releaseSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    lint {
        abortOnError = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
