# builds a Java Spring Boot application with a React frontend
FROM node:22 AS frontend-build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.6-eclipse-temurin-17 AS backend-build
WORKDIR /app
COPY backend/pom.xml .
COPY backend/src ./src
# copy the frontend build output to the backend resources
COPY --from=frontend-build /app/dist ./src/main/resources/static
RUN mvn clean package -DskipTests

FROM cgr.dev/chainguard/wolfi-base:latest
RUN apk add --update --no-cache ffmpeg openjdk-17-default-jvm python3 py3-pip sqlite \
    && pip3 install --no-cache-dir yt-dlp
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
