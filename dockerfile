FROM amazoncorretto:25-jdk

RUN yum install -y shadow-utils && yum clean all

RUN groupadd -g 1000 appgroup && \
    useradd  -u 33 -g 1000 -M -d /app -s /usr/sbin/nologin appuser && \
    mkdir -p /app && chown -R appuser:appgroup /app

WORKDIR /app

COPY target/filedpapers.jar filedpapers.jar
RUN chown appuser:appgroup /app/filedpapers.jar

USER appuser

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/filedpapers.jar"]