import java.util.Properties

// Pass -Ps24 to build a device-tuned APK for a Galaxy S24 Ultra (Android 14+, arm64 only, English only).
val s24Build = providers.gradleProperty("s24").isPresent

// Play Store upload key. keystore.properties is git-ignored; without it, release builds fall back to the debug key.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
val hasUploadKey = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { !keystoreProps.getProperty(it).isNullOrBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.travelogue.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.travelogue.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (s24Build) {
            minSdk = 34 // S24 Ultra shipped with Android 14; lets R8 drop old-API compat paths
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    if (s24Build) {
        androidResources { localeFilters += "en" }
        packaging {
            // Compress dex + native libs in the APK (AGP stores them uncompressed by default at this minSdk)
            dex.useLegacyPackaging = true
            jniLibs.useLegacyPackaging = true
            resources.excludes += listOf(
                "META-INF/**/LICENSE*",
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "kotlin/**.kotlin_builtins",
                "DebugProbesKt.bin",
            )
        }
        dependenciesInfo {
            includeInApk = false
            includeInBundle = false
        }
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(keystoreProps.getProperty("storeFile").replaceFirst("~", System.getProperty("user.home")))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasUploadKey) "upload" else "debug")
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
