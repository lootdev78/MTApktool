plugins {
    `java-library`
}

sourceSets {
    main {
        java.srcDir(rootProject.layout.projectDirectory.dir("third_party/antlr-runtime/src/main/java"))
    }
}

// Vendored from the supplied Android ANTLR 3.5.3 tree. DOTTreeGenerator is
// intentionally excluded (matching Android.bp) so StringTemplate is not a
// runtime dependency of the Android app.
