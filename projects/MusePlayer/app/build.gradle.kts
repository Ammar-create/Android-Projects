import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.velvet.muse"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.velvet.muse"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Material 3 Expressive lives in the 1.5.0-alpha line. In 1.4.0 stable the
    // MaterialExpressiveTheme / ExperimentalMaterial3ExpressiveApi declarations
    // are compiled as `internal`, so they cannot be referenced from app code.
    // This artifact declares kotlin-stdlib 2.2.20, hence the Kotlin 2.2.20 pin.
    implementation("androidx.compose.material3:material3:1.5.0-alpha29")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.12.4")
}
