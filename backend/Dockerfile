FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && mkdir -p /app/data && chown -R app:app /app
WORKDIR /app
COPY --from=build /build/target/android-doctor-backend-0.1.0.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

