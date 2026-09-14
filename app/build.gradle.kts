import java.util.Properties

plugins {
    id("com.android.application")
    alias(libs.plugins.org.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val mtapktoolJavaVersion = providers.gradleProperty("mtapktool.javaVersion")
    .orElse("17")
    .map(String::toInt)
    .get()

val mtapktoolCompileSdk = providers.gradleProperty("mtapktool.compileSdk")
    .orElse("36")
    .map(String::toInt)
    .get()

val mtapktoolMinSdk = providers.gradleProperty("mtapktool.minSdk")
    .orElse("29")
    .map(String::toInt)
    .get()

val mtapktoolTargetSdk = providers.gradleProperty("mtapktool.targetSdk")
    .orElse("36")
    .map(String::toInt)
    .get()

val mtapktoolNdkVersion = providers.gradleProperty("mtapktool.ndkVersion")
    .orElse("29.0.14033849")
    .get()

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val releaseKeystore = file("release.jks")

android {
    namespace = "io.github.lootdev78.mtapktool"

    compileSdk = mtapktoolCompileSdk
    ndkVersion = mtapktoolNdkVersion

    signingConfigs {
        if (releaseKeystore.isFile) {
            create("release") {
                storeFile = releaseKeystore
                storePassword =
                    localProperties.getProperty("KEYSTORE_PASSWORD") ?: "123456"

                keyAlias =
                    localProperties.getProperty("KEY_ALIAS") ?: "my-alias"

                keyPassword =
                    localProperties.getProperty("KEY_PASSWORD") ?: "123456"
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.lootdev78.mtapktool"

        minSdk = mtapktoolMinSdk
        targetSdk = mtapktoolTargetSdk

        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "VERSION_NAME",
            "\"1.0\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isProfileable = false

            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true

            keepDebugSymbols += setOf(
                "**/libaapt2.so",
                "**/libaapt2_33.so",
                "**/libaapt2_35.so"
            )
        }

        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.toVersion(mtapktoolJavaVersion)

        targetCompatibility =
            JavaVersion.toVersion(mtapktoolJavaVersion)
    }
}

kotlin {
    jvmToolchain(mtapktoolJavaVersion)

    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget
                .fromTarget(mtapktoolJavaVersion.toString())
        )
    }
}

/*
 * Keep all transitive AndroidX variants on the
 * SDK-36 / AGP-8 compatible line.
 *
 * This prevents newer KMP Android artifacts from
 * reintroducing versions that require compileSdk 37+.
 */
configurations.configureEach {
    resolutionStrategy.force(
        "androidx.core:core:1.18.0",
        "androidx.core:core-ktx:1.18.0",

        "androidx.lifecycle:lifecycle-runtime:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-android:2.10.0",

        "androidx.lifecycle:lifecycle-runtime-compose:2.10.0",
        "androidx.lifecycle:lifecycle-runtime-compose-android:2.10.0",

        "androidx.lifecycle:lifecycle-viewmodel:2.10.0",
        "androidx.lifecycle:lifecycle-viewmodel-android:2.10.0",

        "androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0",
        "androidx.lifecycle:lifecycle-viewmodel-compose-android:2.10.0"
    )
}

dependencies {

    /*
     * Internal modules
     */
    implementation(project(":apktool-android"))


    /*
     * Coil
     */
    implementation("io.coil-kt.coil3:coil:3.5.0")
    implementation("io.coil-kt.coil3:coil-compose:3.5.0")


    /*
     * Compose / AndroidX
     */
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.compose.material3)


    /*
     * Sora Editor
     */
    implementation(libs.editor)
    implementation(libs.language.textmate)


    /*
     * Kotlin
     */
    implementation(libs.kotlinx.coroutines.android)


    /*
     * Material
     */
    implementation(libs.material)


    /*
     * Compression / archives
     */
    implementation(libs.commons.compress)
    implementation(libs.zip4j)
    implementation(libs.xz)


    /*
     * zstd
     *
     * Use the Android AAR so the JNI libraries
     * are packaged into the APK.
     */
    implementation(
        "com.github.luben:zstd-jni:1.5.7-4@aar"
    )


    /*
     * Compose BOM
     */
    implementation(
        platform(libs.androidx.compose.bom)
    )


    /*
     * Tests
     */
    testImplementation(libs.junit)

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )


    /*
     * Debug tooling
     */
    debugImplementation(
        libs.androidx.compose.ui.tooling
    )
}