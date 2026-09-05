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

# Spring Boot default port
EXPOSE 8082

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]