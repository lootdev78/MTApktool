plugins { `java-library` }

dependencies {
    // Supplied MP-Manager libraries. Keep this protocol layer Android-independent and testable.
    implementation(files("libs/commons-net-3.11.1.jar"))
    implementation(files("libs/ftpserver-core-1.1.1.jar", "libs/ftplet-api-1.1.1.jar", "libs/mina-core-2.0.16.jar", "libs/slf4j-api-1.7.21.jar"))
}
