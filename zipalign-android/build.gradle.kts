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

val nativeZipalign = providers.gradleProperty("apktool.nativeZipalign")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

android {
    namespace = "io.github.muntashirakon.zipalign"
    compileSdk = mtapktoolCompileSdk
    ndkVersion = mtapktoolNdkVersion

    defaultConfig {
        minSdk = mtapktoolMinSdk
        ndk {
            abiFilters += "arm64-v8a"
        }
        if (nativeZipalign) {
            externalNativeBuild {
                cmake { }
            }
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
        targetCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
    }

    if (nativeZipalign) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
        packaging {
            jniLibs {
                keepDebugSymbols += setOf("**/libzipalign.so")
            }
        }
    }
}
