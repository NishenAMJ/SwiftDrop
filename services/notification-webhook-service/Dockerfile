# Build stage: compile the service with the Maven Wrapper (same pattern as the other services).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw ./
COPY pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN ./mvnw -B -DskipTests package

# Run stage: only the JRE and the jar, running as a non-root user.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home swiftdrop
WORKDIR /app
COPY --from=build /workspace/target/notification-webhook-service-*.jar app.jar
USER swiftdrop
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
