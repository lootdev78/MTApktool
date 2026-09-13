pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
    ":app", ":apktool-android",
    ":apksig-android",
    ":zipalign-android",
    ":smali-android",
    ":brut.j.common", ":brut.j.util", ":brut.j.dir", ":brut.j.xml", ":brut.j.yaml",
    ":brut.apktool:apktool-lib", ":brut.apktool:apktool-cli"
)
