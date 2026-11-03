FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B clean package -DskipTests


FROM eclipse-temurin:21-jre

WORKDIR /app

ARG CURL_VERSION

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       curl=${CURL_VERSION} \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --uid 1001 spring

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

RUN chown spring:spring app.jar

USER spring

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
    CMD curl --fail http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]