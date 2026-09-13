# ============================================================
# CloudStorage 后端镜像（包含 gateway/user/core/notification 四个服务）
# 构建:  docker build -t cloudstorage-backend .
# 运行:  docker run -e SERVICE=gateway-service cloudstorage-backend
#
# 国内网络可指定镜像代理，例如：
#   docker build \
#     --build-arg MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21 \
#     --build-arg JRE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre \
#     -t cloudstorage-backend .
# ============================================================

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre

# ---------- 构建阶段 ----------
FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true clean package

# ---------- 运行阶段 ----------
FROM ${JRE_IMAGE}
WORKDIR /app

# 通过 SERVICE 选择要启动的服务: gateway-service / user-service / core-service / notification-service
ENV SERVICE=gateway-service \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Shanghai"

COPY --from=build /workspace/gateway-service/target/gateway-service-*.jar /app/gateway-service.jar
COPY --from=build /workspace/user-service/target/user-service-*.jar /app/user-service.jar
COPY --from=build /workspace/core-service/target/core-service-*.jar /app/core-service.jar
COPY --from=build /workspace/notification-service/target/notification-service-*.jar /app/notification-service.jar

EXPOSE 8080 8081 8082 8083

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/${SERVICE}.jar"]
