FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew :judge-service:bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/judge-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
