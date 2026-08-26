# 1단계: React/Vite 빌드
FROM node:22-alpine AS builder

WORKDIR /app

COPY package*.json ./

RUN npm ci

COPY . .

# 배포 빌드에서는 API를 같은 도메인의 /api 경로로 호출합니다.
# 실제 /api 요청은 Nginx가 Spring Boot로 프록시합니다.
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build


# 2단계: Nginx로 정적 파일 서빙
FROM nginx:1.27-alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]