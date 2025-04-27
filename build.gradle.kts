plugins {
    kotlin("jvm") version "2.1.20"
    id("application")
    id("org.graalvm.buildtools.native") version "0.9.28"
}

group = "com.louloulin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Vert.x dependencies
    implementation(platform("io.vertx:vertx-stack-depchain:4.5.1"))
    implementation("io.vertx:vertx-core")
    implementation("io.vertx:vertx-web")
    implementation("io.vertx:vertx-web-client")
    implementation("io.vertx:vertx-config")
    implementation("io.vertx:vertx-lang-kotlin")
    implementation("io.vertx:vertx-lang-kotlin-coroutines")
    implementation("io.vertx:vertx-health-check")
    implementation("io.vertx:vertx-micrometer-metrics")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.vertx:vertx-junit5")
    testImplementation("io.vertx:vertx-web-client")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.4.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

// Configure GraalVM native image
graalvmNative {
    binaries {
        named("main") {
            imageName.set("apix")
            mainClass.set("com.louloulin.apix.MainKt")
            buildArgs.add("--verbose")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("-H:+PrintClassInitialization")
        }
    }
}

// Configure application
application {
    mainClass.set("com.louloulin.apix.MainKt")
}