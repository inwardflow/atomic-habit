# syntax=docker/dockerfile:1.7
# The jar is platform-independent: build it once on the runner's native platform, even for
# multi-arch images (avoids compiling under QEMU emulation).
FROM --platform=$BUILDPLATFORM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# Ubuntu-based JRE: the Alpine variant is published for linux/amd64 only, which broke arm64 images.
FROM eclipse-temurin:17-jre-noble
WORKDIR /app

# Install curl for healthcheck
RUN apt-get update     && apt-get install -y --no-install-recommends curl     && rm -rf /var/lib/apt/lists/*

# Add a volume pointing to /tmp
VOLUME /tmp

RUN groupadd --system spring && useradd --system --gid spring --no-create-home --shell /usr/sbin/nologin spring
COPY --from=build /app/target/*.jar app.jar

USER spring:spring
# Size the heap from the container memory limit rather than host RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -sf http://localhost:8080/actuator/health || exit 1

# exec so the JVM is PID 1 and receives SIGTERM for graceful shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
