FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN addgroup --system swiftdrop && adduser --system --ingroup swiftdrop swiftdrop
COPY --from=build /workspace/target/delivery-operations-service-*.jar app.jar
USER swiftdrop
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
