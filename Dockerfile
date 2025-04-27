FROM ghcr.io/graalvm/graalvm-ce:latest AS builder

WORKDIR /app

# Copy the project files
COPY . .

# Build the application and create a native image
RUN ./gradlew nativeCompile

# Create a lightweight runtime image
FROM alpine:latest

WORKDIR /app

# Install required dependencies
RUN apk --no-cache add libstdc++ libgomp

# Copy the native executable from the builder stage
COPY --from=builder /app/build/native/nativeCompile/apix /app/apix

# Create config directory
RUN mkdir -p /app/config

# Copy default configuration
COPY --from=builder /app/config/apix.json /app/config/

# Expose the gateway and admin ports
EXPOSE 8080 8081

# Set environment variables
ENV APIX_CONFIG_PATH=/app/config/apix.json

# Run the application
ENTRYPOINT ["/app/apix"]
