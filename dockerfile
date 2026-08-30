FROM amazoncorretto:26-headless

RUN yum install -y shadow-utils \
    && yum clean all \
    && rm -rf /var/cache/yum \
    && groupadd --gid 1000 appgroup \
    && useradd \
        --uid 33 \
        --gid 1000 \
        --no-create-home \
        --home-dir /app \
        --shell /sbin/nologin \
        appuser \
    && mkdir -p /app \
    && chown appuser:appgroup /app

WORKDIR /app

COPY --chown=appuser:appgroup target/filedpapers.jar ./filedpapers.jar

USER appuser

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -jar /app/filedpapers.jar"]