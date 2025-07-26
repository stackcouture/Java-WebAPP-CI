# ------------ Stage 1: Build using Maven ------------
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy only what's needed first for better caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy the source and build the app
COPY src ./src
RUN mvn clean package -DskipTests && cp target/*.jar target/app.jar

# ------------ Stage 2: Create minimal runtime image ------------
FROM eclipse-temurin:17-jre-alpine

# Install curl for healthcheck and create non-root user for security
RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring

USER spring:spring

# Set working directory
WORKDIR /home/spring

# Copy only the final JAR from builder stage
COPY --from=builder /app/target/app.jar app.jar

# Health check to ensure the app is responding
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s \
  CMD curl --silent --fail http://localhost:8080/actuator/health || exit 1

# Expose app port
EXPOSE 8080

# Start the app
ENTRYPOINT ["java", "-jar", "app.jar"]
