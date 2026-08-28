FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew :content-service:bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/content-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
