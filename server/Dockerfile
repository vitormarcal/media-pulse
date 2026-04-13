FROM amazoncorretto:21-alpine AS build

WORKDIR /workspace

COPY ./server/gradlew ./server/gradlew
COPY ./server/gradle ./server/gradle
COPY ./server/build.gradle.kts ./server/build.gradle.kts
COPY ./server/settings.gradle.kts ./server/settings.gradle.kts
COPY ./server/src ./server/src

RUN chmod +x ./server/gradlew
RUN ./server/gradlew -p ./server bootJar --no-daemon

FROM amazoncorretto:21-alpine

WORKDIR /app

COPY --from=build /workspace/server/build/libs/*.jar /app/server.jar

VOLUME /config
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "server.jar", "--spring.config.additional-location=file:/config/"]
