plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.pangolin.Pangolin"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.pangolin.Pangolin"
        minSdk = 24
        targetSdk = 36
        versionCode = 32
        versionName = "0.9.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Per-app release key for the "perapp" build. Set by CI (or a local env) only:
    // PERAPP_KEYSTORE (path), PERAPP_KEYSTORE_PASSWORD, PERAPP_KEY_ALIAS (default "perapp").
    val perappKeystore = System.getenv("PERAPP_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (perappKeystore != null) {
            create("perapp") {
                storeFile = perappKeystore
                storePassword = System.getenv("PERAPP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PERAPP_KEY_ALIAS") ?: "perapp"
                keyPassword = System.getenv("PERAPP_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        // Signed build for self-updating installs (GitHub Releases + Obtainium). Same package as the
        // debug build. versionCode = upstream versionCode * 1000 + CI run number, so it always grows.
        create("perapp") {
            initWith(getByName("debug"))
            isDebuggable = false
            isJniDebuggable = false
            applicationIdSuffix = ".perapp"
            versionNameSuffix = "-perapp" + (System.getenv("PERAPP_BUILD")?.let { ".$it" } ?: "")
            signingConfig = signingConfigs.findByName("perapp")
            matchingFallbacks += listOf("release")
        }
        debug {
            // Installs next to the Play Store app (different package and signing key)
            applicationIdSuffix = ".perapp"
            versionNameSuffix = "-perapp"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }
}

dependencies {
    implementation(project(":tunnel"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // OkHttp
    implementation(libs.okhttp)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Security Crypto for encrypted storage
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.preference)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.biometric.ktx.v140alpha02)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// perapp builds: versionCode = upstream versionCode * 1000 + CI build number, so every CI build is
// an update for Obtainium and upstream version bumps keep it growing. Kept out of defaultConfig so
// upstream's version bumps merge without conflicts.
androidComponents {
    onVariants(selector().withBuildType("perapp")) { variant ->
        val build = System.getenv("PERAPP_BUILD")?.toIntOrNull() ?: return@onVariants
        val base = android.defaultConfig.versionCode ?: 1
        variant.outputs.forEach { output -> output.versionCode.set(base * 1000 + build) }
    }
}
