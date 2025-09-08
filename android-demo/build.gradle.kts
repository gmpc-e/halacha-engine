plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // ← add this
}

android {
    namespace = "com.elad.halacha.demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.elad.halacha.demo"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    // (You can migrate to compilerOptions later; keeping this for now.)
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    kotlinOptions { jvmTarget = "17" }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":core-engine"))
    implementation(platform("androidx.compose:compose-bom:2024.09.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
}
