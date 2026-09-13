# zipalign-java Android vendor notes

Source snapshot: user-supplied `zipalign-java-main.zip`, upstream package `com.iyxan23:zipalign-java`, version declared by the supplied Gradle file as `1.2.2`.

Vendored classes:

- `com.iyxan23.zipalignjava.InvalidZipException`
- `com.iyxan23.zipalignjava.UnsignedByteBufferWrapper`
- `com.iyxan23.zipalignjava.ZipAlign`
- `com.macfaq.io.LittleEndianInputStream`
- `com.macfaq.io.LittleEndianOutputStream`

Android port adjustments:

- CLI `Main` is intentionally not included in the Android library because it calls `System.exit()`.
- The boolean `.so` alignment overload is corrected to disable page alignment when requested.
- EOCD lookup uses the actual EOCD signature offset.
- `.so` page alignment is applied only to STORED entries, matching APK zipalign expectations.
- Oversized local extra fields are rejected before writing.
- The Android facade keeps force/overwrite handling, temporary same-file writes, optional JNI fallback, and verification.
