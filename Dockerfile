# Image of the application only. PostgreSQL runs from the official postgres:16 image (docker-compose.yml).

# Build stage: compiles the jar with the Maven wrapper and the JDK the project targets.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# The wrapper and the pom are copied first, so the dependency layer is only rebuilt when the pom
# changes, not on every source edit.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# Runtime stage: a JRE only image, without Maven, sources or build tools, running as a non-root user.
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app

RUN useradd --system --create-home appuser
USER appuser

COPY --from=build --chown=appuser:appuser /workspace/target/spring-integration-tutorial-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
