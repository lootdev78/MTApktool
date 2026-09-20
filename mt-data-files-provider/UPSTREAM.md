# MTDataFilesProvider integration

Source imported from the user-provided `MTDataFilesProvider-main.zip`.
Upstream project identified by the bundled README as:
`https://github.com/L-JINBIN/MTDataFilesProvider`

MTApktool integration changes:
- converted the Android library build from Groovy/catalog plugins to the MTApktool Kotlin-DSL build profile;
- uses `mtapktool.javaVersion` so `gradlew17` and `gradlew25` compile the same source at the selected Java level;
- uses the root `mtapktool.compileSdk` and `mtapktool.minSdk` properties;
- no JitPack dependency is required because the source is built in-tree;
- provider/activity source and behavior are otherwise kept intact.

The uploaded source archive did not contain a LICENSE/COPYING file. Review upstream licensing before redistribution outside your own project.
