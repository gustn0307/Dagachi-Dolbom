# 다같이돌봄 (Dagachi-Dolbom)

시민의 돌봄 필요 제보와 기관의 검토, 자원봉사자의 안부확인 활동을 연결하는 시민 참여형 지역 돌봄 플랫폼입니다.

## 프로젝트 구성

* Frontend: React (JavaScript / JSX)
* Backend: Java / Spring Boot
* AI Service: Python 3.12 / FastAPI
* Database: PostgreSQL + pgvector
* ORM: Spring Data JPA
* Security: Spring Security + JWT
* DB Migration: Flyway
* File Storage: AWS S3
* Deployment: AWS EC2 + Docker Compose + Nginx + Cloudflare
* CI/CD: GitHub Actions

## 디렉터리 구조

프로젝트는 다음 구조로 구성합니다.

* `backend/` - Spring Boot 백엔드
* `frontend/` - React 프론트엔드
* `ai-service/` - FastAPI 기반 AI 서비스
* `infra/` - 로컬 및 배포용 Docker Compose 설정과 환경변수 예시
* `.github/workflows/` - GitHub Actions Workflow

주요 구조:

```text
Dagachi-Dolbom/
├─ .github/
│  └─ workflows/
│     └─ deploy.yml
├─ ai-service/
├─ backend/
├─ frontend/
├─ infra/
│  ├─ .env.example
│  ├─ docker-compose.local.yml
│  └─ docker-compose.prod.yml
├─ .env
├─ .gitignore
├─ AGENTS.md
└─ README.md
```

실제 비밀값이 들어 있는 프로젝트 루트의 `.env` 파일은 Git에 커밋하지 않습니다.

## Git 브랜치 전략

* `main` - 배포 기준 브랜치
* `dev` - 개발 통합 브랜치
* `feature/*` - 기능 개발 브랜치
* `chore/*` - 배포, 설정, 문서 등 비기능 작업 브랜치

기능 개발은 개인 `feature/*` 브랜치에서 진행한 뒤 Pull Request를 통해 `dev`에 병합합니다.

배포, 설정, 문서 작업은 필요에 따라 `chore/*` 브랜치에서 진행합니다.

검증된 `dev`는 Pull Request를 통해 `main`에 병합합니다.

`main`과 `dev`에는 직접 Push하지 않는 것을 원칙으로 합니다.

`main`에 변경사항이 Push되면 GitHub Actions를 통해 EC2 자동 배포가 실행됩니다.

## DB 설계 기준

개발 착수 기준 DB 설계는 MVP v1.2 Final DB Freeze 버전을 사용합니다.

* 테이블: 14개
* PostgreSQL 사용
* Java Enum + VARCHAR 저장 방식
* PostgreSQL Native ENUM 미사용
* DB Schema 변경은 Flyway Migration으로 관리

## 로컬 개발 환경

로컬 개발과 실제 배포 환경의 Docker 구성을 분리해서 사용합니다.

일반적인 로컬 개발에서는 PostgreSQL만 Docker로 실행합니다.

* PostgreSQL: Docker
* Backend: IntelliJ에서 Spring Boot 직접 실행
* Frontend: Vite 개발 서버 직접 실행
* AI Service: Python 3.12 가상환경에서 Uvicorn 직접 실행

### PostgreSQL 실행

프로젝트 루트에서 실행합니다.

```bash
docker compose -f infra/docker-compose.local.yml up -d
```

실행 상태 확인:

```bash
docker compose -f infra/docker-compose.local.yml ps
```

종료:

```bash
docker compose -f infra/docker-compose.local.yml down
```

로컬 Compose 프로젝트명은 `dagachi-dolbom`으로 고정하여 기존 PostgreSQL Docker Volume을 계속 사용합니다.

로컬 PostgreSQL은 보안을 위해 `127.0.0.1:5432`에만 바인딩됩니다.

### Backend

IntelliJ에서 Spring Boot 애플리케이션을 직접 실행합니다.

기본 로컬 DB 주소:

```text
jdbc:postgresql://localhost:5432/dagachi_dolbom
```

### Frontend

`frontend/` 디렉터리에서 실행합니다.

```bash
npm install
npm run dev
```

로컬 API 주소와 Mock API 사용 여부는 `frontend/.env.local`에서 관리합니다.

예:

```dotenv
VITE_API_BASE_URL=http://localhost:8080
VITE_USE_MOCK_API=true
```

### AI Service

AI Service는 Python 3.12를 사용합니다.

`ai-service/` 디렉터리에서 Python 3.12 가상환경을 생성합니다.

Windows:

```bash
py -3.12 -m venv .venv
```

가상환경 활성화 후:

```bash
pip install -r requirements.txt
uvicorn app.main:app --reload
```

기본 실행 주소:

```text
http://localhost:8000
```

Health Check:

```text
http://localhost:8000/health
```

## 배포 환경

배포 환경에서는 AWS EC2에서 다음 서비스를 Docker Compose로 실행합니다.

* PostgreSQL
* FastAPI AI Service
* Spring Boot Backend
* React + Nginx Frontend

배포용 Compose 파일:

```text
infra/docker-compose.prod.yml
```

배포 서버의 프로젝트 루트에는 실제 환경변수를 저장하는 `.env` 파일이 존재해야 합니다.

환경변수 예시는 다음 파일을 참고합니다.

```text
infra/.env.example
```

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
CORS_ALLOWED_ORIGINS=
```

실제 비밀번호, API Key, JWT Secret 등의 비밀값은 Git에 커밋하지 않습니다.

### 수동 배포 명령

프로젝트 루트에서 실행합니다.

```bash
docker compose \
  -f infra/docker-compose.prod.yml \
  --env-file .env \
  up -d --build
```

실행 상태 확인:

```bash
docker compose \
  -f infra/docker-compose.prod.yml \
  --env-file .env \
  ps
```

로그 확인:

```bash
docker compose \
  -f infra/docker-compose.prod.yml \
  --env-file .env \
  logs -f
```

종료:

```bash
docker compose \
  -f infra/docker-compose.prod.yml \
  --env-file .env \
  down
```

## HTTPS 및 네트워크 구조

서비스 공개 주소:

```text
https://dolbom.hskang.dev
```

Cloudflare DNS에서 `dolbom.hskang.dev`를 AWS EC2의 Elastic IP에 연결합니다.

Cloudflare SSL/TLS 모드는 `Full (strict)`을 사용합니다.

EC2의 Nginx에는 Cloudflare Origin Certificate와 Private Key를 설치합니다.

인증서 파일:

```text
/etc/cloudflare/dolbom-origin.pem
```

Private Key:

```text
/etc/cloudflare/dolbom-origin-key.pem
```

해당 인증서와 Private Key는 Git에 저장하지 않습니다.

Frontend Nginx는 외부에서 다음 포트를 사용합니다.

* `80` - HTTP 요청을 HTTPS로 Redirect
* `443` - HTTPS 서비스

Backend, AI Service, PostgreSQL은 외부에 직접 공개하지 않고 Docker 내부 네트워크에서 통신합니다.

```text
Browser
   ↓ HTTPS
Cloudflare
   ↓ HTTPS
EC2
   ↓
Nginx
   ├─ React 정적 파일
   └─ /api/*
        ↓
     Spring Boot
        ↓
   PostgreSQL / FastAPI / S3
```

Frontend의 `/api/...` 요청은 Nginx가 Docker 내부의 Spring Boot Backend로 프록시합니다.

따라서 배포 환경의 Frontend는 Backend 주소를 브라우저에 직접 노출하지 않고 동일 출처 구조를 사용합니다.

## Backend Readiness Check

배포 상태 확인을 위해 다음 공개 Readiness Endpoint를 사용합니다.

```text
GET /api/health/ready
```

이 Endpoint는 Spring Boot가 요청을 처리할 수 있는지 확인하고 PostgreSQL에 읽기 전용 `SELECT 1`을 실행하여 DB 연결 상태도 함께 확인합니다.

정상 상태에서는 HTTP 200과 다음 형태의 응답을 반환합니다.

```json
{
  "status": "UP"
}
```

DB 주소, 환경변수, 인증정보 등의 운영 세부정보는 응답에 포함하지 않습니다.

## GitHub Actions 자동 배포

Workflow:

```text
.github/workflows/deploy.yml
```

자동 배포는 `main` 브랜치에 Push가 발생하면 실행됩니다.

일반적인 배포 흐름:

```text
feature/* 또는 chore/*
        ↓
       PR
        ↓
       dev
        ↓
   검증 후 PR
        ↓
       main
        ↓
 GitHub Actions
        ↓
   EC2 SSH 접속
        ↓
 Workflow를 발생시킨
 정확한 Commit 배포
        ↓
Docker Compose Build / Up
        ↓
Backend Readiness Check
        ↓
https://dolbom.hskang.dev
```

GitHub Actions에서는 Repository Secrets를 통해 EC2 SSH 접속 정보를 관리합니다.

사용하는 Secret:

```text
EC2_HOST
EC2_USERNAME
EC2_SSH_KEY
EC2_HOST_FINGERPRINT
```

`EC2_SSH_KEY`는 GitHub Actions가 EC2에 인증할 때 사용하는 SSH Private Key입니다.

`EC2_HOST_FINGERPRINT`는 GitHub Actions가 접속하려는 SSH 서버가 미리 확인한 EC2 서버인지 검증하는 데 사용합니다.

배포 Workflow의 외부 SSH Action은 변경 가능한 Version Tag가 아닌 검증한 Full Commit SHA로 고정합니다.

Workflow의 `GITHUB_TOKEN`에는 별도의 GitHub API 권한을 부여하지 않습니다.

GitHub Actions는 EC2에 접속한 뒤 다음 위치에서 배포를 수행합니다.

```text
~/apps/Dagachi-Dolbom
```

Workflow 실행을 발생시킨 Git Commit SHA를 기준으로 EC2의 `main`을 맞춘 뒤 배포합니다.

따라서 실행 도중 더 새로운 `main` Commit이 Push되더라도 현재 Workflow가 의도하지 않은 다른 Commit을 대신 배포하지 않습니다.

배포 명령:

```bash
docker compose \
  -f infra/docker-compose.prod.yml \
  --env-file .env \
  up -d --build
```

배포 후 Docker Compose 상태를 확인하고 다음 Readiness Endpoint를 반복 검사합니다.

```text
https://dolbom.hskang.dev/api/health/ready
```

Readiness Check가 제한 시간 안에 성공하지 않으면 GitHub Actions 배포 작업을 실패 처리합니다.

현재 구성은 자동 Rollback까지 수행하지 않으며, 배포 실패 시 GitHub Actions 실패 로그와 EC2 Docker 상태를 확인하여 수동으로 복구합니다.

## 배포 시 주의사항

* `main`에 병합하면 실제 EC2 자동 배포가 실행됩니다.
* `main` 병합 전 Backend와 Frontend 빌드를 확인합니다.
* 실제 `.env` 파일을 Git에 추가하지 않습니다.
* AWS Key, JWT Secret, SSH Private Key 등의 민감정보를 코드나 문서에 작성하지 않습니다.
* Cloudflare Origin Private Key를 Git에 추가하지 않습니다.
* 인증서 및 Private Key의 로컬 복사본은 Git ignore 정책을 따릅니다.
* 배포 환경의 PostgreSQL 데이터는 Docker Volume을 사용하므로 단순 이미지 재빌드로 삭제되지 않습니다.
* DB Schema 변경은 기존 Migration을 수정하지 않고 새로운 Flyway Migration 파일을 추가합니다.
* GitHub Actions 배포가 실패하면 운영 상태를 확인하고 필요한 경우 수동 복구합니다.

## 현재 배포 주소

```text
https://dolbom.hskang.dev
```
