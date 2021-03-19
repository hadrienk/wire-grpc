import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.30"
    application
    id("com.squareup.wire") version "3.6.1"
}

group = "io.hadrien"
version = "0.0.1-SNAPSHOT"

repositories {
    jcenter()
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://dl.bintray.com/kotlin/kotlinx") }
    maven { url = uri("https://dl.bintray.com/kotlin/ktor") }
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")

    implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.31")

    implementation("com.squareup.wire:wire-runtime:3.6.1")
    implementation("com.squareup.wire:wire-grpc-server:3.6.1")
    implementation("com.squareup.wire:wire-grpc-client:3.6.1")

    implementation("io.grpc:grpc-stub:1.31.1")
    implementation("io.grpc:grpc-netty:1.31.1")
}

// TODO: Figure out a way to compile several roles.
//  https://github.com/square/wire/issues/1914
wire {
    //kotlin {
    //    rpcRole = "client"
    //}
    //kotlin {
    //    rpcRole = "server"
    //}
    //kotlin {
    //    rpcRole = "server"
    //    rpcCallStyle = "blocking"
    //}
}

tasks.withType<com.squareup.wire.gradle.WireTask>() {
    println(this)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClassName = "ServerKt"
}