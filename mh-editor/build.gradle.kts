plugins { id("com.android.library") }

val mtapktoolJavaVersion = providers.gradleProperty("mtapktool.javaVersion").orElse("17").map(String::toInt).get()
val mtapktoolCompileSdk = providers.gradleProperty("mtapktool.compileSdk").orElse("36").map(String::toInt).get()
val mtapktoolMinSdk = providers.gradleProperty("mtapktool.minSdk").orElse("29").map(String::toInt).get()
val mtapktoolNdkVersion = providers.gradleProperty("mtapktool.ndkVersion").orElse("29.0.14033849").get()

android {
    namespace = "modder.hub.editor"
    compileSdk = mtapktoolCompileSdk
    ndkVersion = mtapktoolNdkVersion
    defaultConfig { minSdk = mtapktoolMinSdk }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
        targetCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
    }
}

dependencies {
    implementation(project(":mh-editview"))
    implementation(files("libs/juniversalchardet-2.4.1-SNAPSHOT.jar"))
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
