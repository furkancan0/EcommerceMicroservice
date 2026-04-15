# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy parent pom and common-lib first (cache Maven deps separately)
COPY pom.xml .
COPY common-lib/pom.xml common-lib/
COPY common-lib/src common-lib/src/
COPY api-gateway/pom.xml api-gateway/

# Download dependencies (cached layer — only re-runs when pom changes)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl common-lib,api-gateway dependency:go-offline -q

# Copy source and build
COPY api-gateway/src api-gateway/src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl common-lib,api-gateway -am package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

# Copy the fat jar
COPY --from=builder /workspace/api-gateway/target/*.jar app.jar

# Non-root user for security
USER appuser

# JVM tuning for containers
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseContainerSupport \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
