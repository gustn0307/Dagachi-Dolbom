# Dagachi-Dolbom Development Rules

## 1. Project

다같이돌봄(Dagachi-Dolbom)은 시민 참여형 지역 돌봄 플랫폼이다.

주요 구성은 다음과 같다.

* `backend`: Spring Boot
* `frontend`: React JavaScript/JSX
* `ai-service`: FastAPI
* Database: PostgreSQL + pgvector
* File Storage: AWS S3

## 2. General Rules

* 기존 코드를 수정하기 전에 관련 구조와 사용처를 먼저 확인한다.
* 요청받지 않은 대규모 리팩터링을 임의로 수행하지 않는다.
* 새로운 라이브러리나 프레임워크를 임의로 추가하지 않는다.
* 환경변수, 비밀번호, API Key, JWT Secret 등 민감정보를 코드에 하드코딩하지 않는다.
* `.env` 및 로컬 설정 파일을 Git에 추가하지 않는다.
* 변경 범위를 가능한 작고 명확하게 유지한다.

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

## 4. Database Rules

DB 설계 기준은 MVP v1.2 Final DB Freeze이다.

* 테이블 수: 14개
* Entity 구조를 임의로 변경하지 않는다.
* DB 컬럼 또는 제약조건 변경이 필요한 경우 먼저 설계 변경 여부를 확인한다.
* DB Schema 변경은 Flyway Migration으로 관리한다.
* 이미 적용된 Flyway Migration 파일을 임의로 수정하지 않는다.
* 새로운 DB 변경은 새로운 Migration 파일로 추가한다.

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

## 6. AI Service Rules

* Python과 FastAPI를 사용한다.
* Spring Boot가 일반 비즈니스 로직과 DB 접근의 중심이 된다.
* AI 기능은 필요한 범위에서 FastAPI 서비스로 분리한다.
* AI가 일반 CRUD 또는 핵심 권한 검증을 대신하지 않는다.

## 7. Git Rules

브랜치:

* `main`: 배포 기준
* `dev`: 개발 통합
* `feature/*`: 기능 개발

원칙:

* `main` 직접 Push 금지
* `dev` 직접 Push 금지
* 기능은 `feature/*` 브랜치에서 개발
* Pull Request를 통해 `dev`에 병합
* 검증된 `dev`를 Pull Request를 통해 `main`에 병합

## 8. Before Completing a Task

작업 완료 전 다음을 확인한다.

* 기존 기능을 불필요하게 변경하지 않았는가
* 민감정보가 포함되지 않았는가
* DB 설계와 충돌하지 않는가
* 필요한 테스트를 수행했는가
* 변경 파일과 변경 이유를 설명할 수 있는가

## Git 작업 규칙

* Codex는 사용자의 명시적인 요청 없이 Git 쓰기 작업을 수행하지 않는다.
* 코드 및 파일의 생성·수정까지만 수행한다.
* git add, commit, push, pull, merge, rebase, reset, checkout, switch, restore, stash 등의 명령을 임의로 실행하지 않는다.
* branch, tag, Pull Request, Issue를 임의로 생성·수정·삭제하지 않는다.
* GitHub repository 설정을 임의로 변경하지 않는다.
* git status, git diff, git log, git show 등 읽기 전용 명령은 검토 목적으로 사용할 수 있다.
* 작업 완료 후 변경 파일과 검증 결과를 보고하고 작업을 종료한다.
* Commit과 Push는 사용자가 SourceTree에서 변경사항을 직접 검토한 후 수행한다.