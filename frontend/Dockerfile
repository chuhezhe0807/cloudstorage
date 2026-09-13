# ============================================================
# CloudStorage 前端镜像（Vite 构建 + Nginx 托管/反向代理）
# 构建:  docker build -t cloudstorage-frontend ./frontend
#
# 国内网络可指定镜像代理，例如：
#   docker build \
#     --build-arg NODE_IMAGE=docker.m.daocloud.io/library/node:22-alpine \
#     --build-arg NGINX_IMAGE=docker.m.daocloud.io/library/nginx:1.27-alpine \
#     -t cloudstorage-frontend ./frontend
# ============================================================

ARG NODE_IMAGE=node:22-alpine
ARG NGINX_IMAGE=nginx:1.27-alpine

# ---------- 构建阶段 ----------
FROM ${NODE_IMAGE} AS build
WORKDIR /app
RUN npm install -g pnpm@11

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

COPY . .
RUN pnpm run build

# ---------- 运行阶段 ----------
FROM ${NGINX_IMAGE}
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
