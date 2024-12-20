FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY /target/filedpapers.jar filedpapers.jar
ENTRYPOINT ["java", "-jar", "filedpapers.jar"]