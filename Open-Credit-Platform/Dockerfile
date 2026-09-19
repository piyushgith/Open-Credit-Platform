# Multi-stage build: compile with the JDK, run on a slimmer JRE-only image.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Dependency layer cached separately from source so a source-only change doesn't re-download the
# world.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:25-jre AS run
WORKDIR /app
COPY --from=build /build/target/Open-Credit-Platform-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
