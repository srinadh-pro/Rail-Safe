# Multi-stage build for Render (Render has no native Java runtime).
# Build context is the Git repository root; see ../render.yaml.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace/RailSafe

# Copy the build descriptor first so dependency downloads are cached.
COPY RailSafe/pom.xml ./
COPY RailSafe/src ./src
RUN mvn -B -ntp -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN addgroup --system --gid 10001 railsafe \
    && adduser --system --uid 10001 --ingroup railsafe railsafe

COPY --from=build --chown=railsafe:railsafe /workspace/RailSafe/target/railsafe-1.0.0.jar /app/railsafe.jar

# Spring reads Render's injected PORT through application.properties.
# EXPOSE is documentation only; Render routes to the injected port.
EXPOSE 8080

# Keep heap usage conservative on small Render plans.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -Djava.awt.headless=true"

USER railsafe
ENTRYPOINT ["java", "-jar", "/app/railsafe.jar"]
