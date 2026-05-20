# Stage 1: Build frontend
FROM node:18-alpine AS frontend-build
WORKDIR /app/ui
COPY ui/package.json ui/package-lock.json ./
RUN npm ci
COPY ui/ ./
RUN npm run build

# Stage 2: Build backend
FROM maven:3.9-eclipse-temurin-17-alpine AS backend-build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ ./src/
# Copy frontend dist into static resources for monolithic serving
COPY --from=frontend-build /app/ui/dist/ ./src/main/resources/static/
RUN mvn package -DskipTests -B

# Stage 3: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend-build /app/target/java-ai-langchain4j-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
