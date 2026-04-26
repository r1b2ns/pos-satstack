plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.possatstack.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.possatstack.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Identifier recorded in WalletStorage to detect dev-time backend swaps.
        // When this value changes between builds, BdkOnChainEngine wipes the
        // bdk/ cache and forces a fresh full-scan. Mnemonic and LDK state are
        // never touched by the swap.
        //
        // Active backend: Esplora HTTP. The Kyoto (BIP-157/158 CBF) path is
        // wired in WalletModule but currently inactive — flip the @Binds and
        // change this value to "kyoto" to switch.
        buildConfigField("String", "CHAIN_BACKEND", "\"esplora\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Return default values (0/null) from unmocked android.* calls so
            // code that logs via android.util.Log doesn't blow up in plain
            // JVM unit tests.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    // bdk-android 2.3.x fornece tanto EsploraClient (backend Esplora ativo)
    // quanto CbfBuilder/CbfClient/CbfNode (backend Kyoto, atualmente
    // inativo mas wired em WalletModule). Uma única artifact serve ambos —
    // o swap vive em WalletModule.@Binds + CHAIN_BACKEND em defaultConfig.
    implementation(libs.bdk.android)
    // implementation(libs.floresta)    // ao trocar para Floresta
    implementation(libs.zxing.core)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
