import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val persistentKeystoreFile = file("${rootDir}/debug.keystore")
val encodedKeystoreFile = file("signing/debug.keystore.b64")
if ((!persistentKeystoreFile.exists() || persistentKeystoreFile.length() == 0L) && encodedKeystoreFile.exists()) {
    val cleanBase64 = encodedKeystoreFile.readText().replace("\\s".toRegex(), "")
    persistentKeystoreFile.writeBytes(Base64.getDecoder().decode(cleanBase64))
}

val ghRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val computedVersionCode = if (ghRunNumber != null) 100 + ghRunNumber else 100
val computedVersionName = if (ghRunNumber != null) "1.3.$ghRunNumber" else "1.3.0"
val ghRepository = System.getenv("GITHUB_REPOSITORY") ?: "DefinitelynotRylix/Aurora-Notes"

android {
    namespace = "com.aurora.notes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aurora.notes"
        minSdk = 26
        targetSdk = 35
        versionCode = computedVersionCode
        versionName = computedVersionName

        buildConfigField("String", "GITHUB_REPO", "\"$ghRepository\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("debugConfig") {
            storeFile = persistentKeystoreFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }

    applicationVariants.all {
        outputs.all {
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName =
                "Aurora Notes.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Room SQLite Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Android Biometric Auth
    implementation(libs.androidx.biometric)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
}
