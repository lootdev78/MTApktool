plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":brut.j.common"))
    implementation(libs.commons.io)
    implementation(libs.guava)
}
