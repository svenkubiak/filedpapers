FROM amazoncorretto:25-jdk
WORKDIR /app
COPY target/filedpapers.jar filedpapers.jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/filedpapers.jar"]