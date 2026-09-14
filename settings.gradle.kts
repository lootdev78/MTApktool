import org.gradle.util.GradleVersion

pluginManagement {
    val javaProfile = providers.gradleProperty("mtapktool.javaVersion")
        .orElse("17")
        .get()
        .toIntOrNull()
        ?: throw GradleException("mtapktool.javaVersion must be 17 or 25")

    val defaultAgpVersion = if (javaProfile == 25) "9.0.0" else "8.10.1"
    val agpVersion = providers.gradleProperty("mtapktool.agpVersion")
        .orElse(defaultAgpVersion)
        .get()

    if (javaProfile == 25 && GradleVersion.current() < GradleVersion.version("9.1.0")) {
        throw GradleException(
            "Java 25 profile requires Gradle 9.1.0 or newer. Use ./gradlew25 (or gradlew25.bat)."
        )
    }

    plugins {
        id("com.android.application") version agpVersion
        id("com.android.library") version agpVersion
    }

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MTApktool"
include(
    ":app",
    ":apktool-android",
    ":apksig-android",
    ":zipalign-android",
    ":antlr-runtime",
    ":smali-android",
    ":antisplit-m",
    ":apkextractor",
    ":apkcloner",
    ":mh-editview",
    ":mh-editor",
    ":brut.j.common", ":brut.j.util", ":brut.j.dir", ":brut.j.xml", ":brut.j.yaml",
    ":brut.apktool:apktool-lib", ":brut.apktool:apktool-cli"
)
