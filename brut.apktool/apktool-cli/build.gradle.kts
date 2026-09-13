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
    namespace = "brut.apktool.cli"
    compileSdk = mtapktoolCompileSdk

    defaultConfig {
        minSdk = mtapktoolMinSdk
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
        targetCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
    }

    sourceSets["main"].resources.srcDir("src/main/resources")

    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

dependencies {
    api(project(":brut.apktool:apktool-lib"))
    implementation(libs.commons.cli)
}
