#!/bin/bash

# 设置变量
JAR_FILE="build/libs/apix-1.0.0-fat.jar"
OUTPUT_DIR="build/native"
OUTPUT_NAME="apix"

# 确保输出目录存在
mkdir -p $OUTPUT_DIR

# 构建 fat jar
echo "构建 fat jar..."
./gradlew shadowJar

# 检查 jar 是否存在
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: $JAR_FILE 不存在"
    exit 1
fi

# 构建 native image
echo "构建 native image..."
native-image \
  --verbose \
  --no-fallback \
  --enable-http \
  --enable-https \
  --enable-url-protocols=http,https,unix \
  --enable-all-security-services \
  --report-unsupported-elements-at-runtime \
  -H:+ReportExceptionStackTraces \
  -H:+PrintClassInitialization \
  -H:+StackTrace \
  -H:+JNI \
  -H:+AllowIncompleteClasspath \
  -H:IncludeResources=".*/.*" \
  -H:Name=$OUTPUT_NAME \
  -H:Path=$OUTPUT_DIR \
  -H:ReflectionConfigurationFiles=complete-reflect-config.json \
  -H:ResourceConfigurationFiles=complete-resource-config.json \
  -H:JNIConfigurationFiles=complete-jni-config.json \
  -H:+AddAllCharsets \
  -H:+InlineBeforeAnalysis \
  -H:+InlineDuringParsing \
  -H:+RemoveUnusedSymbols \
  -H:+FoldSecurityManagerGetter \
  -H:+TrustFinalDefaultFields \
  --initialize-at-build-time=ch.qos,org.slf4j,io.netty \
  --initialize-at-run-time=io.vertx.ext.web.client.WebClientOptions,io.netty.channel.epoll,io.netty.channel.unix,io.netty.handler.ssl,org.graalvm \
  --allow-incomplete-classpath \
  --install-exit-handlers \
  --gc=serial \
  -Dio.netty.noUnsafe=false \
  -Dio.netty.leakDetection.level=disabled \
  -Dvertx.disableDnsResolver=true \
  -Dvertx.disableMetrics=true \
  -Dvertx.disableH2c=true \
  -Dvertx.disableWebsockets=false \
  -Dvertx.flashPolicyHandler=false \
  -Dvertx.threadChecks=false \
  -Dvertx.disableContextTimings=true \
  -Dvertx.disableTCCL=true \
  -Dvertx.disableHttpHeadersValidation=true \
  -Dvertx.eventLoopPoolSize=84 \
  -Dvertx.workerPoolSize=140 \
  -Dvertx.preferNativeTransport=true \
  -H:+AllowDeprecatedBuilderClassesOnImageClasspath \
  -jar $JAR_FILE

# 检查构建结果
if [ -f "$OUTPUT_DIR/$OUTPUT_NAME" ]; then
    echo "Native image 构建成功: $OUTPUT_DIR/$OUTPUT_NAME"
    chmod +x "$OUTPUT_DIR/$OUTPUT_NAME"
else
    echo "错误: Native image 构建失败"
    exit 1
fi
