FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x ./gradlew && ./gradlew :coaching-service:bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/coaching-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]