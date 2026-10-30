# The setup service runs Maven to build the generated VSUM projects, so the runtime image needs a
# JDK and Maven. Both stages use the same pinned image.
ARG MAVEN_IMAGE=maven:3.9.16-eclipse-temurin-21

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package \
 && cp target/methodologist-setup-service-*.jar /workspace/app.jar

FROM ${MAVEN_IMAGE}
COPY --from=build /workspace/app.jar /app/app.jar

# Fill the Maven repository of the VSUM builds by building a sample project once. The image then
# contains every dependency of the generated projects, and builds run offline: they do not depend
# on Maven Central being reachable, and the first build after a deployment is as fast as the rest.
# To change versions (setup.vsum.*-version), rebuild the image.
ENV SETUP_VSUM_MAVEN_REPOSITORY=/opt/vsum-m2
RUN java -jar /app/app.jar \
      --spring.main.web-application-type=none \
      --setup.vsum.warmup.enabled=true \
      --setup.vsum.warmup.exit=true \
 && rm -rf /tmp/vsum-build-*

# Offline builds; a dependency missing from the image (e.g. a version overridden at runtime) is
# fetched online once and logged (setup.vsum.online-fallback). The warm-up checks at startup that
# builds work; until then /actuator/health/readiness reports the service as out of service.
ENV SETUP_VSUM_OFFLINE=true \
    SETUP_VSUM_WARMUP_ENABLED=true

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
