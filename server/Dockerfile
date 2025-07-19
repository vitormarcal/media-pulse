# Etapa 1: Build da aplicação
FROM gradle:8.14.3-jdk21-alpine AS builder

WORKDIR /app

# Copie só o necessário para resolver dependências primeiro (aproveita cache)
COPY ./server/gradle ./server/gradle
COPY ./server/gradlew ./server/gradlew
COPY ./server/gradlew.bat ./server/gradlew.bat
COPY ./server/settings.gradle.kts ./server/settings.gradle.kts
COPY ./server/build.gradle.kts ./server/build.gradle.kts

# Roda o download de dependências separadamente
WORKDIR /app/server
RUN ./gradlew build -x test --no-daemon || return 0

# Copie o código (por último — cache invalida só se código mudar)
COPY ./server/src ./src

# Agora sim, build completo
RUN ./gradlew clean build --no-daemon

# Etapa 2: Imagem final com Corretto (sem toolchain de build)
FROM amazoncorretto:21-alpine

WORKDIR /app

# Copie só o jar final
COPY --from=builder /app/server/build/libs/*.jar server.jar

# Configurações da aplicação
VOLUME /config
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "server.jar", "--spring.config.additional-location=file:/config/", "--spring.web.resources.static-locations=file:/app/public"]
