# 다같이돌봄 (Dagachi-Dolbom)

시민의 돌봄 필요 제보와 기관의 검토, 자원봉사자의 안부확인 활동을 연결하는 시민 참여형 지역 돌봄 플랫폼입니다.

## 프로젝트 구성

* Frontend: React (JavaScript / JSX)
* Backend: Java / Spring Boot
* AI Service: Python / FastAPI
* Database: PostgreSQL + pgvector
* ORM: Spring Data JPA
* Security: Spring Security + JWT
* DB Migration: Flyway
* File Storage: AWS S3
* Deployment: AWS + Docker
* CI/CD: GitHub Actions

## 디렉터리 구조

프로젝트는 다음 구조로 구성합니다.

* `backend/` - Spring Boot 백엔드
* `frontend/` - React 프론트엔드
* `ai-service/` - FastAPI 기반 AI 서비스

## Git 브랜치 전략

* `main` - 배포 기준 브랜치
* `dev` - 개발 통합 브랜치
* `feature/*` - 기능별 개발 브랜치

기능 개발은 개인 `feature/*` 브랜치에서 진행한 뒤 Pull Request를 통해 `dev`에 병합합니다.

`main`과 `dev`에는 직접 Push하지 않는 것을 원칙으로 합니다.

## DB 설계 기준

개발 착수 기준 DB 설계는 MVP v1.2 Final DB Freeze 버전을 사용합니다.

* 테이블: 14개
* PostgreSQL 사용
* Java Enum + VARCHAR 저장 방식
* PostgreSQL Native ENUM 미사용
* DB Schema 변경은 Flyway Migration으로 관리

## 개발 및 Docker 실행 방법

로컬 개발 환경과 배포 환경의 Docker 구성을 분리해서 사용합니다.

### 로컬 개발 환경

일반적인 로컬 개발에서는 PostgreSQL만 Docker로 실행합니다.

* PostgreSQL: Docker
* Backend: IntelliJ에서 Spring Boot 직접 실행
* Frontend: Vite 개발 서버 직접 실행
* AI Service: Python 가상환경에서 Uvicorn 직접 실행

프로젝트 루트에서 PostgreSQL을 실행합니다.

```bash
docker compose -f docker-compose.local.yml up -d
```

실행 상태 확인:

```bash
docker compose -f docker-compose.local.yml ps
```

종료:

```bash
docker compose -f docker-compose.local.yml down
```

로컬 PostgreSQL은 보안을 위해 `127.0.0.1:5432`에만 바인딩됩니다.

### 배포 환경

배포 환경에서는 다음 서비스를 모두 Docker Compose로 실행합니다.

* PostgreSQL
* FastAPI AI Service
* Spring Boot Backend
* React + Nginx Frontend

배포 환경에서는 프로젝트 루트의 `.env` 파일에 필요한 환경변수를 설정해야 합니다.

주요 환경변수:

```dotenv
POSTGRES_DB=
POSTGRES_USER=
POSTGRES_PASSWORD=

JWT_SECRET=

AWS_REGION=
AWS_S3_BUCKET=
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=

OPENAI_API_KEY=

AI_SERVICE_CONNECT_TIMEOUT=
AI_SERVICE_READ_TIMEOUT=
```

실제 비밀값이 들어 있는 `.env` 파일은 Git에 커밋하지 않습니다.

배포용 서비스 빌드 및 실행:

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
```

실행 상태 확인:

```bash
docker compose -f docker-compose.prod.yml --env-file .env ps
```

로그 확인:

```bash
docker compose -f docker-compose.prod.yml --env-file .env logs -f
```

종료:

```bash
docker compose -f docker-compose.prod.yml --env-file .env down
```

배포 환경에서는 외부에 Frontend Nginx의 80 포트만 공개하며, Backend, AI Service, PostgreSQL은 Docker 내부 네트워크를 통해 통신합니다.

Frontend는 브라우저의 `/api/...` 요청을 Nginx가 Spring Boot Backend로 프록시하는 동일 출처 구조를 사용합니다.

> 현재 Docker 구성은 HTTP 기준 배포 기반입니다. 실제 공개 배포 시에는 Cloudflare/TLS/HTTPS 및 Forwarded Header 관련 설정을 별도로 적용합니다.
