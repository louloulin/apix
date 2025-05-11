plugins {
    kotlin("jvm") version "1.9.20"
    application
}

group = "com.louloulin.apix"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.vertx:vertx-core:4.4.6")
    implementation("io.vertx:vertx-web:4.4.6")
    implementation("io.vertx:vertx-web-client:4.4.6")
    implementation("io.vertx:vertx-lang-kotlin:4.4.6")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:4.4.6")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    testImplementation("io.vertx:vertx-junit5:4.4.6")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.louloulin.apix.edge.deploy.mesh.MainKt")
}
