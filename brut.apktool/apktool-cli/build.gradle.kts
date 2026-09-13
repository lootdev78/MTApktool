plugins {
    id("com.android.library")
}

android {
    namespace = "brut.apktool.cli"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
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
