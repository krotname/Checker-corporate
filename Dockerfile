FROM eclipse-temurin:21-jre-jammy@sha256:199aebeb3adcde4910695cdebfe782ada38dadb6cc8013159b58d3724451befd AS runtime

LABEL org.opencontainers.image.title="CompanyStatusChecker" \
      org.opencontainers.image.description="Java 21 service for Russian company INN validation and DaData status checks." \
      org.opencontainers.image.source="https://github.com/krotname/CompanyStatusChecker" \
      org.opencontainers.image.licenses="GPL-3.0-or-later"

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app

COPY target/checker-corporate-*.[0-9].jar app.jar

RUN chown app:app /app/app.jar

EXPOSE 8080

USER app

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD ["java", "-cp", "/app/app.jar", "com.krotname.checker.ContainerHealthCheck"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["--server", "8080"]
