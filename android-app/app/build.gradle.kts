plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zeroclaw.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zeroclaw.app"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
            applicationIdSuffix = ".play"
            versionNameSuffix = "-play"
            buildConfigField("String", "DISTRIBUTION", "\"play\"")
            buildConfigField("boolean", "ENABLE_SMS_CALLS", "false")
        }

        create("full") {
            dimension = "distribution"
            applicationIdSuffix = ".full"
            versionNameSuffix = "-full"
            buildConfigField("String", "DISTRIBUTION", "\"full\"")
            buildConfigField("boolean", "ENABLE_SMS_CALLS", "true")
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
    implementation("androidx.core:core-ktx:1.18.0-rc01")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0-alpha01")
    implementation("androidx.compose.ui:ui:1.11.0-alpha05")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.0-alpha05")
    implementation("androidx.compose.material3:material3:1.5.0-alpha14")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("com.google.android.material:material:1.14.0-alpha09")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.0-alpha05")

    debugImplementation("androidx.compose.ui:ui-tooling:1.11.0-alpha05")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.11.0-alpha05")
}
