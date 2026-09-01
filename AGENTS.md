# Dagachi-Dolbom Development Rules

## 1. Project

다같이돌봄(Dagachi-Dolbom)은 시민 참여형 지역 돌봄 플랫폼이다.

주요 구성은 다음과 같다.

* `backend`: Spring Boot
* `frontend`: React JavaScript / JSX
* `ai-service`: Python 3.12 / FastAPI
* `infra`: Docker Compose 및 배포 환경 설정
* `.github/workflows`: GitHub Actions Workflow
* Database: PostgreSQL + pgvector
* File Storage: AWS S3
* Deployment: AWS EC2 + Docker Compose + Nginx + Cloudflare
* CI/CD: GitHub Actions

## 2. General Rules

* 기존 코드를 수정하기 전에 관련 구조와 사용처를 먼저 확인한다.
* 요청받지 않은 대규모 리팩터링을 임의로 수행하지 않는다.
* 새로운 라이브러리나 프레임워크를 임의로 추가하지 않는다.
* 환경변수, 비밀번호, API Key, JWT Secret, SSH Private Key 등 민감정보를 코드에 하드코딩하지 않는다.
* `.env` 및 로컬 설정 파일을 Git에 추가하지 않는다.
* Cloudflare Origin Private Key를 Repository에 추가하지 않는다.
* 인증서 및 Private Key의 로컬 복사본은 `.gitignore` 정책을 따른다.
* 변경 범위를 가능한 작고 명확하게 유지한다.
* 기존 로컬 개발 환경과 실제 배포 환경을 혼동하지 않는다.

## 3. Backend Rules

* Java와 Spring Boot를 사용한다.
* Spring Data JPA를 사용한다.
* Spring Security + JWT 인증 구조를 사용한다.
* DB는 PostgreSQL을 사용한다.
* PostgreSQL Native ENUM 대신 Java Enum을 `EnumType.STRING`으로 저장한다.
* Entity의 Enum 컬럼은 DB에서 VARCHAR로 관리한다.
* Entity 연관관계는 특별한 이유가 없다면 단방향 `ManyToOne(fetch = LAZY)` 중심으로 설계한다.
* Controller에 비즈니스 로직을 작성하지 않는다.
* 비즈니스 로직은 Service 계층에서 처리한다.
* Entity를 API 응답으로 직접 반환하지 않고 DTO를 사용한다.
* 트랜잭션 경계를 명확하게 관리한다.
* 배포 환경에 따라 달라지는 값은 환경변수 또는 설정 파일의 환경변수 바인딩을 사용한다.
* CORS 허용 Origin을 코드에 운영 도메인으로 직접 하드코딩하지 않는다.
* 배포 Readiness Check용 Endpoint는 운영 내부정보를 노출하지 않는다.

## 4. Database Rules

DB 설계 기준은 MVP v1.2 Final DB Freeze이다.

* 테이블 수: 14개
* Entity 구조를 임의로 변경하지 않는다.
* DB 컬럼 또는 제약조건 변경이 필요한 경우 먼저 설계 변경 여부를 확인한다.
* DB Schema 변경은 Flyway Migration으로 관리한다.
* 이미 적용된 Flyway Migration 파일을 임의로 수정하지 않는다.
* 새로운 DB 변경은 새로운 Migration 파일로 추가한다.
* 운영 DB 데이터를 삭제하거나 초기화하는 명령을 임의로 실행하지 않는다.
* Docker Compose 재배포 시 PostgreSQL Volume 유지 여부를 확인한다.

주요 무결성 정책:

* `activity_applications(activity_id, user_id)` UNIQUE
* `activity_records(activity_id)` UNIQUE
* Mileage EARN 중복 지급은 PostgreSQL Partial Unique Index로 방지한다.
* 신청 승인, 승인 취소, 활동 시작 시 CareActivity 단위의 동시성을 고려한다.

## 5. Frontend Rules

* React를 사용한다.
* TypeScript를 사용하지 않는다.
* JavaScript와 JSX를 사용한다.
* API 서버 주소를 컴포넌트에 직접 하드코딩하지 않는다.
* API 호출 설정은 공통 모듈에서 관리한다.
* 환경별 주소는 환경변수로 관리한다.
* 로컬 개발과 배포 환경의 API 설정을 구분한다.
* 배포 환경에서는 동일 출처의 `/api` 경로를 통해 Nginx가 Backend로 프록시한다.
* Mock API 사용 여부는 `VITE_USE_MOCK_API` 환경변수로 명시적으로 관리한다.
* 실제 배포 환경에서 Mock API가 의도치 않게 활성화되지 않도록 한다.

## 6. AI Service Rules

* Python 3.12와 FastAPI를 사용한다.
* Spring Boot가 일반 비즈니스 로직과 DB 접근의 중심이 된다.
* AI 기능은 필요한 범위에서 FastAPI 서비스로 분리한다.
* AI가 일반 CRUD 또는 핵심 권한 검증을 대신하지 않는다.
* FastAPI와 Spring Boot 간 주소는 환경변수 기반으로 관리한다.
* API Key 등 AI 관련 민감정보를 코드에 작성하지 않는다.

## 7. Infrastructure Rules

배포 관련 파일은 `infra/`에서 관리한다.

```text
infra/
├─ .env.example
├─ docker-compose.local.yml
└─ docker-compose.prod.yml
```

### Local

로컬 개발용 Compose:

```text
infra/docker-compose.local.yml
```

로컬 개발에서는 원칙적으로 PostgreSQL만 Docker로 실행한다.

```bash
docker compose -f infra/docker-compose.local.yml up -d
```

로컬 Compose 프로젝트명은 `dagachi-dolbom`으로 유지하여 기존 PostgreSQL Docker Volume을 계속 사용한다.

Backend, Frontend, AI Service는 각각 개발 도구에서 직접 실행한다.

로컬 개발을 위해 운영용 Compose 구성을 사용할 필요가 없다.

### Production

배포용 Compose:

```text
infra/docker-compose.prod.yml
```

배포 환경에서는 다음 서비스를 Docker Compose로 실행한다.

* PostgreSQL
* FastAPI AI Service
* Spring Boot Backend
* React + Nginx Frontend

실제 환경변수는 EC2 프로젝트 루트의 `.env`에서 관리한다.

```text
~/apps/Dagachi-Dolbom/.env
```

환경변수 예시는 다음 파일에서 관리한다.

```text
infra/.env.example
```

실제 `.env` 값은 Repository에 커밋하지 않는다.

## 8. HTTPS and Cloudflare Rules

운영 서비스 주소:

```text
https://dolbom.hskang.dev
```

Cloudflare를 통해 HTTPS를 제공한다.

Cloudflare SSL/TLS 모드는 `Full (strict)`을 사용한다.

Nginx는 HTTP 80 요청을 HTTPS 443으로 Redirect한다.

Cloudflare Origin Certificate 관련 파일은 EC2에만 저장한다.

```text
/etc/cloudflare/dolbom-origin.pem
/etc/cloudflare/dolbom-origin-key.pem
```

특히 Private Key는 Repository, 문서, 로그에 포함하지 않는다.

Backend, AI Service, PostgreSQL 포트를 외부 인터넷에 직접 공개하지 않는다.

Frontend Nginx를 외부 진입점으로 사용한다.

## 9. Backend Readiness Rules

배포 후 Backend와 PostgreSQL 연결 상태를 확인하기 위해 다음 Endpoint를 사용한다.

```text
GET /api/health/ready
```

이 Endpoint는 인증 없이 접근 가능해야 하며 다음 조건을 만족한다.

* Spring Boot가 요청을 처리할 수 있는지 확인한다.
* PostgreSQL에 읽기 전용 `SELECT 1`을 실행한다.
* DB 주소, 계정정보, 환경변수, 예외 상세 등 운영 내부정보를 응답에 포함하지 않는다.
* 정상 상태에서는 HTTP 200을 반환한다.
* DB 연결 실패 시 성공 응답을 반환하지 않는다.

해당 Endpoint는 비즈니스 기능이 아니라 배포 및 운영 Readiness 확인용이다.

## 10. GitHub Actions Rules

GitHub Actions Workflow:

```text
.github/workflows/deploy.yml
```

`main` 브랜치에 Push가 발생하면 EC2 자동 배포가 실행된다.

배포 과정:

```text
main push
   ↓
GitHub Actions
   ↓
EC2 SSH
   ↓
Workflow를 발생시킨 정확한 Git Commit으로 이동
   ↓
Docker Compose Build / Up
   ↓
Backend Readiness Check
```

GitHub Actions에서 사용하는 민감정보는 Repository Secrets로 관리한다.

현재 사용하는 Secret:

```text
EC2_HOST
EC2_USERNAME
EC2_SSH_KEY
EC2_HOST_FINGERPRINT
```

* `EC2_SSH_KEY`는 GitHub Actions가 EC2에 인증할 때 사용하는 SSH Private Key이다.
* `EC2_HOST_FINGERPRINT`는 GitHub Actions가 접속하려는 서버가 미리 확인한 EC2 서버인지 검증하는 데 사용한다.
* Secret 값을 Workflow 파일에 직접 작성하지 않는다.
* 외부 GitHub Action은 가능한 경우 검증한 Full Commit SHA로 고정한다.
* 현재 배포 Workflow는 SSH Action을 Full Commit SHA로 고정한다.
* Workflow에 불필요한 `GITHUB_TOKEN` 권한을 부여하지 않는다.
* Workflow 실행을 발생시킨 `${{ github.sha }}` 기준 Commit을 배포한다.
* 배포 후 `/api/health/ready`를 통해 Backend와 DB Readiness를 확인한다.
* Readiness Check가 제한 시간 안에 성공하지 않으면 Workflow를 실패 처리한다.
* 현재 자동 Rollback은 구현하지 않았으므로 실패 시 운영 상태를 확인하고 수동 복구한다.

GitHub Actions Workflow를 수정할 때는 실제 운영 배포에 영향을 줄 수 있음을 고려한다.

`main`에 Workflow 변경사항이 병합되면 자동 배포가 실행될 수 있으므로 반드시 변경 내용을 검토한다.

## 11. Git Rules

브랜치:

* `main`: 배포 기준
* `dev`: 개발 통합
* `feature/*`: 기능 개발
* `chore/*`: 배포, 환경설정, 문서 등 비기능 작업

원칙:

* `main` 직접 Push 금지
* `dev` 직접 Push 금지
* 기능은 `feature/*` 브랜치에서 개발
* 배포 및 설정 작업은 필요에 따라 `chore/*` 브랜치에서 개발
* Pull Request를 통해 `dev`에 병합
* 검증된 `dev`를 Pull Request를 통해 `main`에 병합
* `main` 병합은 실제 자동 배포를 발생시킬 수 있으므로 운영 변경으로 취급한다.
* 기능 브랜치에서 최신 `dev`가 필요하면 충돌 여부를 확인한 뒤 병합한다.

최초 GitHub Actions 도입처럼 `chore/*`에서 `main`으로 직접 PR이 필요한 예외 상황은 팀 합의 후 수행하며 일반적인 개발 흐름으로 사용하지 않는다.

## 12. Deployment Safety Rules

배포 관련 변경 시 다음을 확인한다.

* `infra/docker-compose.local.yml`이 기존 로컬 개발 환경과 Docker Volume을 깨뜨리지 않는가
* `infra/docker-compose.prod.yml`이 EC2 환경에서 정상적으로 해석되는가
* `.env` 또는 Secret이 Git 변경사항에 포함되지 않았는가
* Backend, Frontend, AI Service 이미지가 정상적으로 빌드되는가
* HTTPS 및 Nginx 설정을 변경했다면 Cloudflare 구조와 충돌하지 않는가
* Docker Volume을 실수로 제거하지 않는가
* `down -v` 또는 운영 DB 초기화 명령을 임의로 실행하지 않는가
* Backend Readiness Endpoint가 정상 동작하는가
* `main`에 병합하기 전에 실제 배포 영향을 설명할 수 있는가

Compose 설정 확인 예:

```bash
docker compose -f infra/docker-compose.local.yml config --quiet
```

```bash
docker compose -f infra/docker-compose.prod.yml --env-file .env config --quiet
```

## 13. Before Completing a Task

작업 완료 전 다음을 확인한다.

* 기존 기능을 불필요하게 변경하지 않았는가
* 민감정보가 포함되지 않았는가
* DB 설계와 충돌하지 않는가
* 필요한 테스트 또는 빌드를 수행했는가
* 변경 파일과 변경 이유를 설명할 수 있는가
* 배포 관련 변경이라면 로컬 개발 환경과 운영 환경 모두에 미치는 영향을 확인했는가
* `main` 병합 시 자동 배포가 실행된다는 점을 고려했는가

## 14. Git 작업 규칙

* Codex는 사용자의 명시적인 요청 없이 Git 쓰기 작업을 수행하지 않는다.
* 코드 및 파일의 생성·수정까지만 수행한다.
* `git add`, `commit`, `push`, `pull`, `merge`, `rebase`, `reset`, `checkout`, `switch`, `restore`, `stash` 등의 명령을 임의로 실행하지 않는다.
* branch, tag, Pull Request, Issue를 임의로 생성·수정·삭제하지 않는다.
* GitHub Repository 설정을 임의로 변경하지 않는다.
* GitHub Actions Secret을 임의로 생성, 수정, 삭제하지 않는다.
* 운영 EC2의 환경변수, 인증서, SSH 설정을 사용자의 명시적인 요청 없이 변경하지 않는다.
* `git status`, `git diff`, `git log`, `git show` 등 읽기 전용 명령은 검토 목적으로 사용할 수 있다.
* 작업 완료 후 변경 파일과 검증 결과를 보고하고 작업을 종료한다.
* Commit과 Push는 사용자가 SourceTree에서 변경사항을 직접 검토한 후 수행한다.
