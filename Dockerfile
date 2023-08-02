FROM gradle:8.1-jdk17 AS build
WORKDIR /home/gradle/src
ENV GRADLE_USER_HOME /gradle

COPY build.gradle.kts settings.gradle.kts ./

COPY --chown=gradle:gradle . .
RUN gradle build --info && \
    java -Djarmode=layertools -jar build/libs/*.jar extract

FROM gcr.io/distroless/java17:nonroot
WORKDIR /opt/kafka-streams-template
COPY --from=build /home/gradle/src/dependencies/ ./
COPY --from=build /home/gradle/src/spring-boot-loader/ ./
COPY --from=build /home/gradle/src/application/ ./
COPY HealthCheck.java .

USER nonroot
ARG GIT_REF=""
ARG GIT_URL=""
ARG BUILD_TIME=""
ARG VERSION=0.0.0
ENV APP_VERSION=${VERSION} \
    SPRING_PROFILES_ACTIVE="prod"
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=90", "org.springframework.boot.loader.JarLauncher"]

HEALTHCHECK --interval=25s --timeout=3s --retries=2 CMD ["java", "HealthCheck.java", "||", "exit", "1"]

LABEL org.opencontainers.image.created=${BUILD_TIME} \
    org.opencontainers.image.authors="Paul-Chrisitan Volkmer, Jakub Lidke" \
    org.opencontainers.image.source=${GIT_URL} \
    org.opencontainers.image.version=${VERSION} \
    org.opencontainers.image.revision=${GIT_REF} \
    org.opencontainers.image.vendor="" \
    org.opencontainers.image.title="etl-processor" \
    org.opencontainers.image.description="Relay application between Onkostar and bwHc for pseudonymization or anonymization of patient data and profide additional monitoring of processed data."
