plugins {
    // Exact Android Gradle Plugin baseline used by the supplied Apktool-A port.
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false

    // MTExplorer is Kotlin/Compose. AGP 8 does not embed Kotlin, so apply it explicitly.
    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

allprojects {
    group = "io.github.lootdev78.mtapktool"
    version = "1.0.0"
}

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
