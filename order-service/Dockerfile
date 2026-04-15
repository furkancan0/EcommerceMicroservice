# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY common-lib/src common-lib/src/
COPY order-service/pom.xml order-service/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl common-lib,order-service dependency:go-offline -q
COPY order-service/src order-service/src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl common-lib,order-service -am package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /workspace/order-service/target/*.jar app.jar
USER appuser
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
