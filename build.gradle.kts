plugins {
    java
    kotlin("jvm")
}

dependencies {
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.bouncycastle:bcprov-jdk15on:1.58")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.58")
    testImplementation("junit:junit:4.13.2")
}

group = "net.dongliu"
version = "2.6.10-cliuff"
description = "apk-parser"
java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17
kotlin.jvmToolchain(17)