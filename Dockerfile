FROM maven:3.9.13-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package \
    && cp target/KhetWallah-0.0.1-SNAPSHOT.jar /tmp/khetwallah.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd --system khetwallah \
    && useradd --system --gid khetwallah --home-dir /app --no-create-home khetwallah \
    && mkdir /app/uploads \
    && chown khetwallah:khetwallah /app/uploads

COPY --from=build --chown=khetwallah:khetwallah /tmp/khetwallah.jar /app/app.jar

# A starting point for a small instance; tune after observing actual memory use.
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:+UseSerialGC -Djava.awt.headless=true"
ENV UPLOAD_DIR=/app/uploads

USER khetwallah
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
