plugins { id("com.android.library") }

val mtapktoolJavaVersion = providers.gradleProperty("mtapktool.javaVersion").orElse("17").map(String::toInt).get()
val mtapktoolCompileSdk = providers.gradleProperty("mtapktool.compileSdk").orElse("36").map(String::toInt).get()
val mtapktoolMinSdk = providers.gradleProperty("mtapktool.minSdk").orElse("29").map(String::toInt).get()
val mtapktoolNdkVersion = providers.gradleProperty("mtapktool.ndkVersion").orElse("29.0.14033849").get()

android {
    namespace = "mt.modder.hub.apkCloner"
    compileSdk = mtapktoolCompileSdk
    ndkVersion = mtapktoolNdkVersion
    defaultConfig { minSdk = mtapktoolMinSdk }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
        targetCompatibility = JavaVersion.toVersion(mtapktoolJavaVersion)
    }
}

dependencies {
    implementation(files("libs/AXMLPrinter.jar", "libs/Axml2xml-v1.jar", "libs/arsc-parser.jar", "libs/bin-zip.jar"))
    implementation(libs.guava)
    implementation(libs.xmlpull)
}
