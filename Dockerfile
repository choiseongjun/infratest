# Build Stage
FROM gradle:8.7.0-jdk17-alpine AS build
WORKDIR /app

# Copy gradle files and source code
COPY build.gradle settings.gradle ./
COPY src ./src

# Build the application jar (excluding tests for faster builds)
RUN gradle bootJar --no-daemon -x test

# Run Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built JAR from the build stage (Gradle build output is in build/libs/)
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port 8080
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java", "-jar", "app.jar"]
