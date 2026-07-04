# ---- 构建阶段：用 Maven + JDK17 打出可执行 jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先拷 pom 拉依赖，利用 Docker 层缓存（源码变了不必重拉依赖）
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- 运行阶段：仅 JRE17 + ffmpeg（视频链路合成/转码需要）----
FROM eclipse-temurin:17-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/aimv-backend-0.1.0-SNAPSHOT.jar app.jar
# 媒体持久化目录（compose 里挂卷）
RUN mkdir -p /app/storage/media
EXPOSE 8081
# 以持久化 profile 启动；数据库等配置全部通过环境变量注入（见 docker-compose.yml）
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=postgres"]
