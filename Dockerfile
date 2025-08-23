FROM 104824081961.dkr.ecr.ap-south-1.amazonaws.com/eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring

USER spring:spring
WORKDIR /home/spring

COPY --chown=spring:spring target/*.jar app.jar

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s \
  CMD curl --silent --fail http://localhost:8080/demo || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
