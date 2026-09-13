plugins {
    `java-library`
}

dependencies {
    implementation(project(":brut.j.common"))
    implementation(libs.commons.io)
    implementation(libs.guava)
}
