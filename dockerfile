FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/filedpapers.jar filedpapers.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/filedpapers.jar"]