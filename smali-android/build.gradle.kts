import org.gradle.api.plugins.antlr.AntlrTask

plugins {
    `java-library`
    antlr
}

val smaliRoot = rootProject.layout.projectDirectory.dir("third_party/smali-src")
val generatedJflex = layout.buildDirectory.dir("generated-src/jflex/main")

sourceSets {
    main {
        java.srcDirs(
            smaliRoot.dir("dexlib2/src/main/java"),
            smaliRoot.dir("third_party/dexlib2/src/main/java"),
            smaliRoot.dir("util/src/main/java"),
            smaliRoot.dir("third_party/util/src/main/java"),
            smaliRoot.dir("baksmali/src/main/java"),
            smaliRoot.dir("third_party/baksmali/src/main/java"),
            smaliRoot.dir("smali/src/main/java"),
            smaliRoot.dir("third_party/smali/src/main/java"),
            generatedJflex
        )
        resources.srcDirs(
            smaliRoot.dir("smali/src/main/resources"),
            smaliRoot.dir("baksmali/src/main/resources")
        )
        // Keep local copies so Gradle/ANTLR never resolves through a missing
        // smali-android/src/main/antlr/../../../../... base directory on Android IDEs.
        antlr.srcDir("src/main/antlr")
    }
}

val jflex by configurations.creating

dependencies {
    api(libs.guava)
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.beust:jcommander:1.82")
    api(project(":antlr-runtime"))
    antlr("org.antlr:antlr:3.5.3")
    jflex("de.jflex:jflex:1.9.1")
}

tasks.withType<AntlrTask>().configureEach {
    outputDirectory = layout.buildDirectory.dir("generated-src/antlr/main/com/android/tools/smali/smali").get().asFile
}

val generateSmaliLexer by tasks.registering(JavaExec::class) {
    val outDir = generatedJflex.map { it.dir("com/android/tools/smali/smali") }
    inputs.file(smaliRoot.file("smali/src/main/jflex/smaliLexer.jflex"))
    outputs.dir(outDir)
    classpath = jflex
    mainClass.set("jflex.Main")
    doFirst { outDir.get().asFile.mkdirs() }
    args("-d", outDir.get().asFile.absolutePath, smaliRoot.file("smali/src/main/jflex/smaliLexer.jflex").asFile.absolutePath)
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateSmaliLexer, tasks.named("generateGrammarSource"))
}


tasks.processResources {
    filesMatching(listOf("smali.properties", "baksmali.properties")) {
        expand("version" to "3.0.9")
    }
}
