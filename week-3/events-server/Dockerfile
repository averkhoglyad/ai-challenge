# ── Stage 1: Resolve dependencies (cached unless build scripts change) ──
FROM eclipse-temurin:21-jdk AS deps
WORKDIR /app

# Gradle wrapper & project metadata
COPY gradlew gradle/ settings.gradle.kts gradle.properties ./

# Convention plugins (buildSrc)
COPY buildSrc/ buildSrc/

# All module build scripts (for dependency resolution)
COPY common-core/build.gradle.kts common-core/
COPY common-test/build.gradle.kts common-test/
COPY week-3/events-server/build.gradle.kts week-3/events-server/

# Download dependencies — this layer is cached as long as build scripts don't change
RUN ./gradlew :week-3:events-server:dependencies --configure-on-demand --no-daemon || true

# ── Stage 2: Build the application ──
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Reuse cached Gradle home from deps stage
COPY --from=deps /root/.gradle /root/.gradle
COPY --from=deps /app/.gradle ./.gradle
COPY --from=deps /app/buildSrc buildSrc/
COPY --from=deps /app/gradlew /app/gradle/ /app/settings.gradle.kts /app/gradle.properties ./

# Copy full source tree
COPY . .

# Build executable JAR
RUN ./gradlew :week-3:events-server:bootJar --no-daemon

# ── Stage 3: Minimal runtime image ──
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /data
COPY --from=build /app/week-3/events-server/build/libs/*.jar app.jar
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-Dapp.db.path=/data/events.db", "-jar", "app.jar"]
