plugins {
    kotlin("jvm") version "2.0.21"
    id("application")
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks {
    wrapper {
        gradleVersion = "8.8"
    }

    withType<Test> {
        useJUnitPlatform()
    }
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass = "com.practice.interview.Main"
}
