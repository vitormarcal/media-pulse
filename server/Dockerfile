FROM amazoncorretto:21-alpine

WORKDIR /app

# Copie o jar já construído localmente
COPY ./server/build/libs/*.jar server.jar

# Configurações da aplicação
VOLUME /config
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "server.jar", "--spring.config.additional-location=file:/config/"]
