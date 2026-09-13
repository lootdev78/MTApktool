MTApktool overlay: SDK 36 / AGP 8.10.1 AAR metadata fix

Apply:
  Extract this ZIP over the repository root and overwrite existing files.

Changes:
  gradle/libs.versions.toml
    androidx.core:core/core-ktx       1.19.0 -> 1.18.0
    androidx.lifecycle compose       2.11.0 -> 2.10.0

Reason:
  Core 1.19.0 and Lifecycle 2.11.0 declare compileSdk 37 / AGP 9.1+
  requirements. MTApktool intentionally stays on the Apktool-A toolchain:
  AGP 8.10.1, compileSdk/targetSdk 36, NDK 29.0.14033849.

No Apktool-A source/build modules are changed by this overlay.
