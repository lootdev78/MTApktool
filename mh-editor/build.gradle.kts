plugins { id("com.android.library") }

val javaProfile = providers.gradleProperty("mtapktool.javaVersion").orElse("17").map(String::toInt).get()
val compileSdkValue = providers.gradleProperty("mtapktool.compileSdk").orElse("36").map(String::toInt).get()
val minSdkValue = providers.gradleProperty("mtapktool.minSdk").orElse("29").map(String::toInt).get()
val ndkValue = providers.gradleProperty("mtapktool.ndkVersion").orElse("29.0.14033849").get()

android {
    namespace = "modder.hub.editor"
    compileSdk = compileSdkValue
    ndkVersion = ndkValue
    defaultConfig { minSdk = minSdkValue }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(javaProfile)
        targetCompatibility = JavaVersion.toVersion(javaProfile)
    }
}

dependencies {
    implementation("androidx.activity:activity:1.13.0")
    implementation(libs.androidx.core.ktx)
    implementation(project(":mh-editview"))
    implementation(files("libs/juniversalchardet-2.4.1-SNAPSHOT.jar"))
}
