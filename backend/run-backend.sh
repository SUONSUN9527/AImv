#!/usr/bin/env bash
# AImv 后端启动脚本（持久化模式）
# ---------------------------------------------------------------------------
# 为什么要这个脚本：
#   - 默认（不带 profile）跑的是 in-memory 仓储，数据只在内存，重启即丢——
#     这会让「历史生成 / 资产库」在后端重启后全部消失。
#   - 带 --spring.profiles.active=postgres 时，所有仓储切到 PostgresXxxRepository，
#     chain run / 产物 / 项目 / 阶段 / 知识库全部落库，跨重启保留，历史不再被删除。
#   - 单元测试仍走内存库（测试未激活 postgres profile），本脚本只影响「运行」，不影响「测试」。
#
# 依赖：本机 aimv-pgvector 容器（端口 55432，库 aimv，账号 aimv/aimv，已装 pgvector）。
#   注意别连成 5432——那是另一个项目 jobseek 的库。默认 URL 已指向 55432。
#
# 用法：  bash run-backend.sh            # 前台
#         nohup bash run-backend.sh &    # 后台
# API Key：DashScope 等第三方 Key 通过前端「能力配置」页录入，加密存 postgres，无需写死在这里。
# ---------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")"

JAR=target/aimv-backend-0.1.0-SNAPSHOT.jar
PORT="${AIMV_SERVER_PORT:-8081}"

# 若 jar 不存在则先构建（复用本机 maven，缺失则用 wrapper）
if [ ! -f "$JAR" ]; then
  MVN="$HOME/apache-maven-3.9.16/bin/mvn"
  [ -x "$MVN" ] || MVN=./mvnw
  echo "[run-backend] 未找到 jar，先构建：$MVN -q package -DskipTests"
  "$MVN" -q package -DskipTests
fi

echo "[run-backend] 启动：端口=$PORT，profile=postgres（持久化）"
exec java -jar "$JAR" \
  --server.port="$PORT" \
  --spring.profiles.active=postgres
