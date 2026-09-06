# =========================
# Stage 1: Build
# =========================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom first for better Docker layer caching
COPY pom.xml .

RUN mvn dependency:go-offline -B

# Copy source
COPY src ./src

# Build application
RUN mvn clean package -DskipTests


# =========================
# Stage 2: Runtime
# =========================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the generated Spring Boot JAR
COPY --from=builder /app/target/*.jar app.jar

# An image is only ever run on a server, so it defaults to the production profile.
# The local profile is for `mvn spring-boot:run`, not for a container.
ENV SPRING_PROFILES_ACTIVE=prod

# Container memory is the ceiling the JVM should size its heap against, not the
# host's. Without this a 512 MB container is handed a heap it cannot honour.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Nothing here needs root. Alpine's `nobody` avoids adding a user just to drop to it.
USER nobody

# Spring Boot default port
EXPOSE 8082

# Shell form so $JAVA_OPTS is expanded; exec so java still receives SIGTERM as PID 1
# and Spring runs its shutdown hooks instead of being killed after the stop timeout.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
