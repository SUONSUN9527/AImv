# ---- 构建阶段：Node 构建静态资源 ----
FROM node:20-alpine AS build
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY . .
# 置空 API base：前端用相对地址(/api、/media)，由 nginx 反代到后端，前后端同源、免 CORS
ENV VITE_AIMV_API_BASE_URL=""
RUN npm run build

# ---- 运行阶段：nginx 托管 SPA 并反代后端 ----
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
