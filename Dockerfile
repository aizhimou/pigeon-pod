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

# Final runtime image
FROM cgr.dev/chainguard/wolfi-base:latest

# Install all required packages including Deno
RUN apk add --update --no-cache \
    ffmpeg \
    openjdk-17-default-jvm \
    python3 \
    py3-pip \
    sqlite \
    deno \
    && pip3 install --no-cache-dir "yt-dlp[default,curl-cffi]"

WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar

ENV LANG=C.UTF-8
ENV JAVA_OPTS="-Dfile.encoding=UTF-8"
ENV PIGEON_FFMPEG_LOCATION=/usr/bin/ffmpeg

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
