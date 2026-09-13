plugins {
    `java-library`
}

dependencies {
    implementation(project(":brut.j.common"))
    api(libs.xmlpull)
}
