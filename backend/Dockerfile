FROM gradle:8.5-jdk17 AS build
WORKDIR /workspace/backend

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY proto ./proto
COPY src ./src

RUN chmod +x gradlew && ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/backend/build/libs/*.jar /app/app.jar

EXPOSE 50051 8000

CMD ["java", "-jar", "/app/app.jar"]
