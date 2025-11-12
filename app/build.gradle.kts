plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "xyz.blacksheep.mjolnir"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "xyz.blacksheep.mjolnir"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
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
    implementation("io.ktor:ktor-client-android:2.3.11")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation(libs.androidx.compose.runtime.saveable)
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.core:core-splashscreen:1.0.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
