plugins {
    id("com.android.library")
}

android {
    namespace = "brut.apktool.cli"
    compileSdk = 36
    ndkVersion = "29.0.14033849"

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
