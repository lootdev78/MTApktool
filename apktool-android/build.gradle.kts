plugins {
    id("com.android.library")
}

val mtapktoolJavaVersion = providers.gradleProperty("mtapktool.javaVersion")
    .orElse("17")
    .map(String::toInt)
    .get()

val mtapktoolCompileSdk = providers.gradleProperty("mtapktool.compileSdk").orElse("36").map(String::toInt).get()
val mtapktoolMinSdk = providers.gradleProperty("mtapktool.minSdk").orElse("29").map(String::toInt).get()
val mtapktoolTargetSdk = providers.gradleProperty("mtapktool.targetSdk").orElse("36").map(String::toInt).get()
val mtapktoolNdkVersion = providers.gradleProperty("mtapktool.ndkVersion").orElse("29.0.14033849").get()

android {
    namespace = "io.github.apktool.android"
    compileSdk = mtapktoolCompileSdk
    ndkVersion = mtapktoolNdkVersion

    defaultConfig {
        minSdk = mtapktoolMinSdk
        ndk { abiFilters += "arm64-v8a" }
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
        targetCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
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
    api(project(":brut.apktool:apktool-lib"))
    api(project(":brut.apktool:apktool-cli"))
    api(project(":apksig-android"))
    api(project(":zipalign-android"))
    implementation(libs.commons.cli)
}
