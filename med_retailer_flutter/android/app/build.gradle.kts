plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.pims.medretailers"
    compileSdk = 36
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val keystorePropertiesFile = rootProject.file("key.properties")
    val keystoreProperties = java.util.Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            val keyPath = System.getenv("KEYSTORE_PATH")
                ?: keystoreProperties.getProperty("storeFile")
                ?: ""
            val storePass = System.getenv("KEYSTORE_PASSWORD")
                ?: keystoreProperties.getProperty("storePassword")
                ?: ""
            val keyAliasProp = System.getenv("KEY_ALIAS")
                ?: keystoreProperties.getProperty("keyAlias")
                ?: "release_key"
            val keyPass = System.getenv("KEY_PASSWORD")
                ?: keystoreProperties.getProperty("keyPassword")
                ?: ""

            if (keyPath.isNotEmpty()) {
                val keystoreFile = file(keyPath)
                if (keystoreFile.exists() && storePass.isNotEmpty()) {
                    storeFile = keystoreFile
                    storePassword = storePass
                    keyAlias = keyAliasProp
                    keyPassword = keyPass
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.pims.medretailers"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "2.0.0-beta"
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
