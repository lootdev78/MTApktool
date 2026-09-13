plugins {
    id("com.android.library")
}

val mtapktoolJavaVersion = providers.gradleProperty("mtapktool.javaVersion")
    .orElse("17")
    .map(String::toInt)
    .get()

android {
    namespace = "io.github.apktool.android.apksig"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
        targetCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
    }

    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}
