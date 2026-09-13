plugins {
    id("com.android.library")
}

android {
    namespace = "brut.androlib"
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
    api(project(":brut.j.common"))
    api(project(":brut.j.util"))
    api(project(":brut.j.dir"))
    api(project(":brut.j.xml"))
    api(project(":brut.j.yaml"))

    implementation(project(":smali-android"))
    implementation(project(":antlr-runtime"))
    implementation(libs.guava)
    implementation(libs.commons.io)
    implementation(libs.commons.text)
}
