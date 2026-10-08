# =========================
# Stage 1: Build
# =========================
# Same Maven as .mvn/wrapper/maven-wrapper.properties (3.9.12), so the image,
# CI and a laptop all build with one version. Pinned to the patch release:
# a moving tag like 3.9 can change the build without any change in this repo.
FROM maven:3.9.12-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom first for better Docker layer caching
COPY pom.xml .

RUN mvn dependency:go-offline -B

# Copy source
COPY src ./src

# Tests run in CI before the image is built; repeating them here would only
# make every image build slower.
RUN mvn clean package -DskipTests -B


# =========================
# Stage 2: Runtime
# =========================
FROM eclipse-temurin:17.0.20.1_1-jre-alpine

# Which commit and CI build this image came from - `docker inspect` shows them,
# and Kubernetes keeps them with the running Pod's image. CI passes real values;
# a local build gets "dev".
ARG VERSION=dev
ARG GIT_SHA=dev
ARG BUILD_NUMBER=dev
LABEL org.opencontainers.image.title="taskpluse-api" \
      org.opencontainers.image.source="https://github.com/sokhin-devops/taskpluse_api" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      build.number="${BUILD_NUMBER}"

WORKDIR /app

# Copy the generated Spring Boot JAR
COPY --from=builder /app/target/*.jar app.jar

# An image is only ever run on a server, so it defaults to the production profile.
# The local profile is for `mvn spring-boot:run`, not for a container.
ENV SPRING_PROFILES_ACTIVE=prod

# Container memory is the ceiling the JVM should size its heap against, not the
# host's. Without this a 512 MB container is handed a heap it cannot honour.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Nothing here needs root. 65534 is Alpine's `nobody`, written as a number:
# Kubernetes' runAsNonRoot can only verify a numeric user, and refuses to start
# an image whose USER is a name.
USER 65534:65534

# Spring Boot default port
EXPOSE 8082

# For `docker run`; Kubernetes ignores this and uses its own probes.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8082/actuator/health/liveness || exit 1

# Shell form so $JAVA_OPTS is expanded; exec so java still receives SIGTERM as PID 1
# and Spring runs its shutdown hooks instead of being killed after the stop timeout.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
