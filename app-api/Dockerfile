ARG JAR_FILE=app-api/build/libs/app-api-*-SNAPSHOT.jar

FROM eclipse-temurin:25-jre-alpine AS extractor
ARG JAR_FILE
WORKDIR /extractor
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
ENV TZ=UTC

RUN addgroup -S appgroup && adduser -S appuser -G appgroup && chown -R appuser:appgroup /app

COPY --from=extractor --chown=appuser:appgroup /extractor/extracted/dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /extractor/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=appuser:appgroup /extractor/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=appuser:appgroup /extractor/extracted/application/ ./

USER appuser
EXPOSE 8082

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "org.springframework.boot.loader.launch.JarLauncher"]
