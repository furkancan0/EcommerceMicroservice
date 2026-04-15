# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY common-lib/src common-lib/src/
COPY auth-service/pom.xml auth-service/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl common-lib,auth-service dependency:go-offline -q
COPY auth-service/src auth-service/src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl common-lib,auth-service -am package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /workspace/auth-service/target/*.jar app.jar
USER appuser
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8085
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
