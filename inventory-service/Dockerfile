# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY common-lib/src common-lib/src/
COPY inventory-service/pom.xml inventory-service/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl common-lib,inventory-service dependency:go-offline -q
COPY inventory-service/src inventory-service/src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl common-lib,inventory-service -am package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /workspace/inventory-service/target/*.jar app.jar
USER appuser
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8083
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
