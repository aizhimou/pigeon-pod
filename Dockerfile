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
RUN mkdir -p /data/logs /tmp/pigeon-pod

WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ENV LANG=C.UTF-8
ENV JAVA_OPTS="-Dfile.encoding=UTF-8"
ENV PIGEON_FFMPEG_LOCATION=/usr/bin/ffmpeg
ENV PIGEON_LOG_FILE=/data/logs/pigeon-pod.log

EXPOSE 8080
ENTRYPOINT ["docker-entrypoint.sh"]
