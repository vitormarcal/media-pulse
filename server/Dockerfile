FROM node:22-alpine AS frontend-build

WORKDIR /workspace/frontend

COPY ./frontend/package.json ./package.json
COPY ./frontend/package-lock.json ./package-lock.json
RUN npm ci

COPY ./frontend ./
RUN npm run build

FROM amazoncorretto:21-alpine AS build

WORKDIR /workspace

COPY ./server/gradlew ./server/gradlew
COPY ./server/gradle ./server/gradle
COPY ./server/build.gradle.kts ./server/build.gradle.kts
COPY ./server/settings.gradle.kts ./server/settings.gradle.kts
COPY ./server/src ./server/src
COPY --from=frontend-build /workspace/frontend/.output ./frontend/.output

RUN chmod +x ./server/gradlew
RUN ./server/gradlew -p ./server bootJar --no-daemon

FROM amazoncorretto:21-alpine

WORKDIR /app

COPY --from=build /workspace/server/build/libs/*.jar /app/server.jar

VOLUME /config
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "server.jar", "--spring.config.additional-location=file:/config/"]
