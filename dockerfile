FROM eclipse-temurin:21-jre-ubi9-minimal
WORKDIR /app
COPY target/filedpapers.jar filedpapers.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/filedpapers.jar"]