plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.zenith.tvos"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.zenith.tvos"
        minSdk = 24 // Android 7.0 Nougat (Supports 5-10 year old hardware)
        targetSdk = 34
        versionCode = 100
        versionName = "1.0.0-zenith"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Strict deterministic proguard optimization for zero bloat
        ndk {
            abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // default fallback for local dev
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-Xjvm-default=all"
        )
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.recyclerview)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
}
