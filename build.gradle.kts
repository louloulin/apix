plugins {
    kotlin("jvm") version "1.9.20"
    id("application")
    id("org.graalvm.buildtools.native") version "0.9.28"
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    implementation("io.vertx:vertx-auth-jwt")
    implementation("io.vertx:vertx-auth-common")
    implementation("io.vertx:vertx-auth-htpasswd")
    implementation("io.vertx:vertx-circuit-breaker")
    implementation("io.vertx:vertx-dropwizard-metrics")
    implementation("io.vertx:vertx-zipkin")

    // Cluster support
    implementation("io.vertx:vertx-zookeeper")
    implementation("io.vertx:vertx-hazelcast")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")

    // JCTools - high performance concurrent data structures

    // HdrHistogram - high dynamic range histogram
    implementation("org.hdrhistogram:HdrHistogram:2.1.12")

    // OpenTelemetry - 暂时注释掉，等待集成到主代码库
    // implementation("io.opentelemetry:opentelemetry-api:1.24.0")
    // implementation("io.opentelemetry:opentelemetry-sdk:1.24.0")
    // implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.24.0")
    // implementation("io.opentelemetry:opentelemetry-semconv:1.24.0-alpha")
    // implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api:1.24.0")

    // GraalVM WebAssembly support - 使用最新版本
    compileOnly("org.graalvm.polyglot:polyglot:24.2.1")
    compileOnly("org.graalvm.polyglot:wasm:24.2.1")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.vertx:vertx-junit5")
    testImplementation("io.vertx:vertx-web-client")
    testImplementation("io.vertx:vertx-unit")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("junit:junit:4.13.2")
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
    metadataRepository {
        enabled.set(false)
    }
}

// Configure application
application {
    mainClass.set("com.louloulin.apix.MainKt")
}

// Configure shadowJar
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("apix")
    archiveClassifier.set("fat")
    archiveVersion.set("1.0.0")
    manifest {
        attributes(mapOf(
            "Main-Class" to "com.louloulin.apix.MainKt"
        ))
    }
    mergeServiceFiles()
}

// 创建 Vert.x 10万并发配置文件
tasks.register("createVertxConfig") {
    doLast {
        val configDir = file("src/main/resources")
        configDir.mkdirs()

        val availableProcessors = Runtime.getRuntime().availableProcessors()
        println("检测到 $availableProcessors 个处理器")

        val configFile = file("${configDir}/vertx-100k.json")
        configFile.writeText("""
            {
              "eventLoopPoolSize": ${availableProcessors * 2},
              "workerPoolSize": ${availableProcessors * 8},
              "internalBlockingPoolSize": ${availableProcessors * 4},
              "blockedThreadCheckInterval": 10000,
              "maxEventLoopExecuteTime": 60000000000,
              "maxWorkerExecuteTime": 600000000000,
              "warningExceptionTime": 30000000000,
              "fileResolverCachingEnabled": true,
              "preferNativeTransport": true,
              "eventBus": {
                "acceptBacklog": 100000,
                "clientAuth": "NONE",
                "connectTimeout": 30000,
                "idleTimeout": 0,
                "receiveBufferSize": 262144,
                "reconnectAttempts": 0,
                "reconnectInterval": 1000,
                "reuseAddress": true,
                "reusePort": true,
                "sendBufferSize": 262144,
                "soLinger": -1,
                "ssl": false,
                "tcpKeepAlive": true,
                "tcpNoDelay": true,
                "trafficClass": -1,
                "trustAll": true
              },
              "httpServerOptions": {
                "acceptBacklog": 100000,
                "compressionLevel": 1,
                "compressionSupported": false,
                "decompressionSupported": false,
                "handle100ContinueAutomatically": true,
                "idleTimeout": 300,
                "maxChunkSize": 65536,
                "maxHeaderSize": 32768,
                "maxInitialLineLength": 16384,
                "maxWebSocketFrameSize": 262144,
                "maxWebSocketMessageSize": 1048576,
                "reuseAddress": true,
                "reusePort": true,
                "tcpFastOpen": true,
                "tcpKeepAlive": true,
                "tcpNoDelay": true,
                "tcpQuickAck": true
              },
              "http2Settings": {
                "headerTableSize": 8192,
                "initialWindowSize": 1048576,
                "maxConcurrentStreams": 100000,
                "maxFrameSize": 16384,
                "maxHeaderListSize": 32768,
                "pushEnabled": false
              }
            }
        """.trimIndent())

        println("Vert.x 10万并发配置文件已创建: ${configFile.absolutePath}")
    }
}

// 构建 Native Image
tasks.register("buildNativeImage") {
    dependsOn("createVertxConfig", "nativeCompile")

    doLast {
        println("Native Image 构建完成: ${file("build/native/nativeCompile/apix").absolutePath}")

        // 复制配置文件
        val buildDir = file("build/native/nativeCompile")
        copy {
            from(rootDir) {
                include("config-control-plane.json")
                include("config-node1.json")
                include("config-node2.json")
                include("config-node3.json")
                include("config-standalone.json")
            }
            into(buildDir)
        }

        println("配置文件已复制到: ${buildDir.absolutePath}")
    }
}

// 设置系统参数
tasks.register("setupSystemParams") {
    doLast {
        println("设置系统参数...")
        val os = org.gradle.internal.os.OperatingSystem.current()

        if (os.isLinux) {
            println("尝试设置 Linux 内核参数...")
            exec {
                commandLine("sh", "-c", "ulimit -n 1000000 || echo \"警告: 无法设置文件描述符限制，可能需要 root 权限\"")
            }
            exec {
                commandLine("sudo", "sysctl", "-w", "net.core.somaxconn=100000")
                isIgnoreExitValue = true
            }
            exec {
                commandLine("sudo", "sysctl", "-w", "net.ipv4.tcp_max_syn_backlog=100000")
                isIgnoreExitValue = true
            }
            exec {
                commandLine("sudo", "sysctl", "-w", "net.ipv4.ip_local_port_range=\"1024 65535\"")
                isIgnoreExitValue = true
            }
            exec {
                commandLine("sudo", "sysctl", "-w", "net.ipv4.tcp_tw_reuse=1")
                isIgnoreExitValue = true
            }
            exec {
                commandLine("sudo", "sysctl", "-w", "net.ipv4.tcp_fin_timeout=5")
                isIgnoreExitValue = true
            }
            exec {
                commandLine("sudo", "sysctl", "-w", "net.core.netdev_max_backlog=250000")
                isIgnoreExitValue = true
            }
        } else if (os.isMacOsX) {
            println("在 macOS 上设置系统参数...")
            exec {
                commandLine("sh", "-c", "ulimit -n 1000000 || echo \"警告: 无法设置文件描述符限制，可能需要 root 权限\"")
            }
            exec {
                commandLine("sudo", "sysctl", "-w", "kern.maxfiles=1000000")
                isIgnoreExitValue = true
            }
            exec {
                commandLine("sudo", "sysctl", "-w", "kern.maxfilesperproc=1000000")
                isIgnoreExitValue = true
            }
        }

        println("系统参数设置完成！")
    }
}

// 启动 Native 集群
tasks.register("startNativeCluster") {
    dependsOn("startControlPlane", "startNode1", "startNode2")

    doLast {
        println("APIX Gateway Native Image 集群已启动！")

        // 检查集群状态
        println("检查集群状态...")
        exec {
            commandLine("curl", "-s", "http://localhost:8070/api/cluster/status")
            standardOutput = System.out
        }

        // 等待用户输入
        println("\n按 Enter 键停止集群...")
        System.`in`.read()

        // 停止集群
        println("停止集群...")
        project.exec {
            commandLine("./gradlew", "stopNativeCluster")
        }
    }
}

// 启动控制平面节点
tasks.register("startControlPlane") {
    dependsOn("buildNativeImage", "setupSystemParams")

    doLast {
        // 运行 Native Image
        println("启动控制平面节点...")
        val buildDir = file("build/native/nativeCompile")

        // 启动控制平面节点
        val controlPlaneProcess = ProcessBuilder()
            .directory(buildDir)
            .command("./apix", "-Dapix.config.path=config-control-plane.json")
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        println("控制平面节点已启动，进程ID: ${controlPlaneProcess.pid()}")

        // 等待控制平面节点启动
        println("等待控制平面节点启动...")
        Thread.sleep(10000)

        // 检查控制平面节点是否正常启动
        println("检查控制平面节点是否正常启动...")
        val controlPlaneCheck = ProcessBuilder()
            .command("curl", "-s", "http://localhost:8070/ping")
            .start()
        val controlPlaneCheckResult = controlPlaneCheck.waitFor()

        if (controlPlaneCheckResult != 0) {
            println("警告: 无法访问控制平面节点，请检查日志")
            controlPlaneProcess.destroy()
            return@doLast
        }

        println("控制平面节点启动成功！")
    }
}

// 启动数据平面节点1
tasks.register("startNode1") {
    dependsOn("buildNativeImage", "setupSystemParams")

    doLast {
        // 运行 Native Image
        println("启动数据平面节点1...")
        val buildDir = file("build/native/nativeCompile")

        // 启动数据平面节点1
        val node1Process = ProcessBuilder()
            .directory(buildDir)
            .command("./apix", "-Dapix.config.path=config-node1.json")
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        println("数据平面节点1已启动，进程ID: ${node1Process.pid()}")

        // 等待数据平面节点1启动
        println("等待数据平面节点1启动...")
        Thread.sleep(10000)

        // 检查数据平面节点1是否正常启动
        println("检查数据平面节点1是否正常启动...")
        val node1Check = ProcessBuilder()
            .command("curl", "-s", "http://localhost:9280/ping")
            .start()
        val node1CheckResult = node1Check.waitFor()

        if (node1CheckResult != 0) {
            println("警告: 无法访问数据平面节点1，请检查日志")
            node1Process.destroy()
            return@doLast
        }

        println("数据平面节点1启动成功！")
    }
}

// 启动数据平面节点2
tasks.register("startNode2") {
    dependsOn("buildNativeImage", "setupSystemParams")

    doLast {
        // 运行 Native Image
        println("启动数据平面节点2...")
        val buildDir = file("build/native/nativeCompile")

        // 启动数据平面节点2
        val node2Process = ProcessBuilder()
            .directory(buildDir)
            .command("./apix", "-Dapix.config.path=config-node2.json")
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        println("数据平面节点2已启动，进程ID: ${node2Process.pid()}")

        // 等待数据平面节点2启动
        println("等待数据平面节点2启动...")
        Thread.sleep(10000)

        // 检查数据平面节点2是否正常启动
        println("检查数据平面节点2是否正常启动...")
        val node2Check = ProcessBuilder()
            .command("curl", "-s", "http://localhost:9090/ping")
            .start()
        val node2CheckResult = node2Check.waitFor()

        if (node2CheckResult != 0) {
            println("警告: 无法访问数据平面节点2，请检查日志")
            node2Process.destroy()
            return@doLast
        }

        println("数据平面节点2启动成功！")
    }
}

// 启动数据平面节点3
tasks.register("startNode3") {
    dependsOn("buildNativeImage", "setupSystemParams")

    doLast {
        // 运行 Native Image
        println("启动数据平面节点3...")
        val buildDir = file("build/native/nativeCompile")

        // 启动数据平面节点3
        val node3Process = ProcessBuilder()
            .directory(buildDir)
            .command("./apix", "-Dapix.config.path=config-node3.json")
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        println("数据平面节点3已启动，进程ID: ${node3Process.pid()}")

        // 等待数据平面节点3启动
        println("等待数据平面节点3启动...")
        Thread.sleep(10000)

        // 检查数据平面节点3是否正常启动
        println("检查数据平面节点3是否正常启动...")
        val node3Check = ProcessBuilder()
            .command("curl", "-s", "http://localhost:10090/ping")
            .start()
        val node3CheckResult = node3Check.waitFor()

        if (node3CheckResult != 0) {
            println("警告: 无法访问数据平面节点3，请检查日志")
            node3Process.destroy()
            return@doLast
        }

        println("数据平面节点3启动成功！")
    }
}

// 启动单机模式
tasks.register("startStandalone") {
    dependsOn("buildNativeImage", "setupSystemParams")

    doLast {
        // 运行 Native Image
        println("启动单机模式...")
        val buildDir = file("build/native/nativeCompile")

        // 复制配置文件
        copy {
            from(rootDir) {
                include("config-standalone.json")
            }
            into(buildDir)
        }

        // 启动单机模式
        val standaloneProcess = ProcessBuilder()
            .directory(buildDir)
            .command("./apix", "-Dapix.config.path=config-standalone.json")
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        println("单机模式已启动，进程ID: ${standaloneProcess.pid()}")

        // 等待单机模式启动
        println("等待单机模式启动...")
        Thread.sleep(10000)

        // 检查单机模式是否正常启动
        println("检查单机模式是否正常启动...")
        val standaloneCheck = ProcessBuilder()
            .command("curl", "-s", "http://localhost:8080/ping")
            .start()
        val standaloneCheckResult = standaloneCheck.waitFor()

        if (standaloneCheckResult != 0) {
            println("警告: 无法访问单机模式，请检查日志")
            standaloneProcess.destroy()
            return@doLast
        }

        println("单机模式启动成功！")

        // 等待用户输入
        println("\n按 Enter 键停止单机模式...")
        System.`in`.read()

        // 停止单机模式
        println("停止单机模式...")
        standaloneProcess.destroy()

        println("===== APIX Gateway Native Image 单机模式已停止 =====")
    }
}

// 单机模式性能测试
tasks.register("benchmarkStandalone") {
    dependsOn("buildNativeImage", "setupSystemParams")

    doLast {
        // 运行 Native Image
        println("启动单机模式进行性能测试...")
        val buildDir = file("build/native/nativeCompile")

        // 复制配置文件
        copy {
            from(rootDir) {
                include("config-standalone.json")
            }
            into(buildDir)
        }

        // 启动单机模式
        val standaloneProcess = ProcessBuilder()
            .directory(buildDir)
            .command("./apix", "-Dapix.config.path=config-standalone.json")
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        println("单机模式已启动，进程ID: ${standaloneProcess.pid()}")

        // 等待单机模式启动
        println("等待单机模式启动...")
        Thread.sleep(10000)

        // 检查单机模式是否正常启动
        println("检查单机模式是否正常启动...")
        val standaloneCheck = ProcessBuilder()
            .command("curl", "-s", "http://localhost:8080/ping")
            .start()
        val standaloneCheckResult = standaloneCheck.waitFor()

        if (standaloneCheckResult != 0) {
            println("警告: 无法访问单机模式，请检查日志")
            standaloneProcess.destroy()
            return@doLast
        }

        println("单机模式启动成功，开始性能测试...")

        // 运行 k6 性能测试
        try {
            exec {
                workingDir = rootDir
                commandLine("k6", "run", "loadtest-high-perf.js")
                standardOutput = System.out
            }
        } catch (e: Exception) {
            println("性能测试运行失败: ${e.message}")
        } finally {
            // 停止单机模式
            println("测试完成，停止单机模式...")
            standaloneProcess.destroy()

            println("===== APIX Gateway Native Image 单机模式性能测试完成 =====")
        }
    }
}

// 集群模式性能测试
tasks.register("benchmarkCluster") {
    dependsOn("startControlPlane", "startNode1", "startNode2")

    doLast {
        println("集群模式已启动，开始性能测试...")

        // 运行 k6 性能测试
        try {
            exec {
                workingDir = rootDir
                commandLine("k6", "run", "loadtest-high-perf.js")
                standardOutput = System.out
            }
        } catch (e: Exception) {
            println("性能测试运行失败: ${e.message}")
        } finally {
            // 停止集群
            println("测试完成，停止集群...")
            project.exec {
                commandLine("./gradlew", "stopNativeCluster")
            }

            println("===== APIX Gateway Native Image 集群模式性能测试完成 =====")
        }
    }
}

// 停止单机模式
tasks.register("stopStandalone") {
    doLast {
        println("停止 APIX Gateway Native Image 单机模式...")

        // 查找并停止单机模式进程
        if (org.gradle.internal.os.OperatingSystem.current().isLinux ||
            org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            exec {
                commandLine("sh", "-c", "pkill -f './apix -Dapix.config.path=config-standalone.json' || true")
                isIgnoreExitValue = true
            }
        } else if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            exec {
                commandLine("cmd", "/c", "taskkill /F /IM apix.exe || exit /b 0")
                isIgnoreExitValue = true
            }
        }

        println("===== APIX Gateway Native Image 单机模式已停止 =====")
    }
}

// 停止 Native 集群
tasks.register("stopNativeCluster") {
    doLast {
        println("停止 APIX Gateway Native Image 集群...")

        // 查找并停止所有 apix 进程
        if (org.gradle.internal.os.OperatingSystem.current().isLinux ||
            org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            exec {
                commandLine("sh", "-c", "pkill -f './apix -Dapix.config.path' || true")
                isIgnoreExitValue = true
            }
        } else if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
            exec {
                commandLine("cmd", "/c", "taskkill /F /IM apix.exe || exit /b 0")
                isIgnoreExitValue = true
            }
        }

        println("===== APIX Gateway Native Image 集群已停止 =====")
    }
}