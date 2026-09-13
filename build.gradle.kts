plugins {
    // Keep the Android plugin pair exactly on the known-good Apktool-A baseline.
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false

    // MTExplorer is Kotlin/Compose; AGP 8.x does not embed Kotlin, so these stay
    // explicit while the Android plugin itself remains identical to Apktool-A.
    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    group = "io.github.lootdev78.mtapktool"
    version = "1.0.0"
}

// Preserve Apktool-A's Java/toolchain policy. The app module overrides its own
// Kotlin/Java target to 17; the vendored Apktool/smali Java sources stay Java 8.
subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(17))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(8)
        }
    }
}
