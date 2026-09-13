plugins {
    // The AGP version is selected in settings.gradle.kts so the Java 17 and
    // Java 25 build profiles can use Gradle-compatible Android plugins.
    id("com.android.application") apply false
    id("com.android.library") apply false

    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val mtapktoolJavaVersion = providers.gradleProperty("mtapktool.javaVersion")
    .orElse("17")
    .map { value ->
        value.toIntOrNull()
            ?: throw GradleException("mtapktool.javaVersion must be 17 or 25, got '$value'")
    }
    .get()

if (mtapktoolJavaVersion !in setOf(17, 25)) {
    throw GradleException(
        "Unsupported mtapktool.javaVersion=$mtapktoolJavaVersion. Supported profiles: 17, 25"
    )
}

allprojects {
    group = "io.github.lootdev78.mtapktool"
    version = "1.0.0"
}

// JVM-only source modules inherit the selected profile centrally. Android
// modules read the same property in their android.compileOptions blocks.
subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(mtapktoolJavaVersion))
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(mtapktoolJavaVersion)
        }
    }
}

tasks.register("verifyJavaProfile") {
    group = "verification"
    description = "Verifies the selected MTApktool Java build profile."
    doLast {
        val defaultAgpVersion = if (mtapktoolJavaVersion == 25) "9.0.0" else "8.10.1"
        val agpVersion = providers.gradleProperty("mtapktool.agpVersion").orElse(defaultAgpVersion).get()
        val runtimeJavaVersion = Runtime.version().feature()
        println("MTApktool Java profile: $mtapktoolJavaVersion")
        println("MTApktool AGP profile: $agpVersion")
        println("Gradle runtime Java: ${System.getProperty("java.version")}")
        check(mtapktoolJavaVersion == 17 || mtapktoolJavaVersion == 25)
        check(runtimeJavaVersion == mtapktoolJavaVersion) {
            "Profile Java $mtapktoolJavaVersion requires Gradle runtime JDK $mtapktoolJavaVersion, " +
                "but Gradle is running on Java $runtimeJavaVersion"
        }
    }
}
