plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val vCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
val ks = file("release.jks")

android {
    namespace = "net.pakeng.koreksiljk"
    compileSdk = 34

    defaultConfig {
        applicationId = "net.pakeng.koreksiljk"
        minSdk = 26
        targetSdk = 34
        versionCode = vCode
        versionName = "1.0.$vCode"
    }

    signingConfigs {
        create("release") {
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("KEYSTORE_PASS") ?: "koreksiljk"
                keyAlias = "ljk"
                keyPassword = System.getenv("KEYSTORE_PASS") ?: "koreksiljk"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ks.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
