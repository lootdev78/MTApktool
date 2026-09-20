plugins {
    id("com.android.library")
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

android {
    namespace = "cn.mt2.datafilesprovider"
    compileSdk = mtapktoolCompileSdk

    defaultConfig {
        minSdk = mtapktoolMinSdk
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
