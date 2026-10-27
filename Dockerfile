FROM eclipse-temurin:21-jdk

RUN apt-get update \
 && apt-get install -y maven \
 && rm -rf /var/lib/apt/lists/*

COPY methodologist-setup-service-0.0.1-SNAPSHOT.jar /app/app.jar

# generated projects and constraint files may have non-ASCII names
ENV LANG=C.UTF-8

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]