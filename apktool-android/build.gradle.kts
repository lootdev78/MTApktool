plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.apktool.android"
    compileSdk = 36
    ndkVersion = "29.0.14033849"

    defaultConfig {
        minSdk = 26
        ndk { abiFilters += "arm64-v8a" }
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += setOf("**/libaapt2.so", "**/libaapt2_33.so", "**/libaapt2_35.so")
        }
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    implementation(project(":brut.apktool:apktool-lib"))
    implementation(project(":brut.apktool:apktool-cli"))
    implementation(project(":apksig-android"))
    implementation(project(":zipalign-android"))
    implementation(libs.commons.cli)
}
