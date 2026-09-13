plugins {
    id("com.android.library")
}

val mtapktoolJavaVersion = providers.gradleProperty("mtapktool.javaVersion")
    .orElse("17")
    .map(String::toInt)
    .get()

val nativeZipalign = providers.gradleProperty("apktool.nativeZipalign")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

android {
    namespace = "io.github.muntashirakon.zipalign"
    compileSdk = 36
    ndkVersion = "29.0.14033849"

    defaultConfig {
        minSdk = 29
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
