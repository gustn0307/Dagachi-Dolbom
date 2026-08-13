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

프로젝트 초기 구축 과정에서 다음 구조로 구성할 예정입니다.

* `backend/` - Spring Boot 백엔드
* `frontend/` - React 프론트엔드
* `ai-service/` - FastAPI 기반 AI 서비스
* `infra/` - Docker 및 배포 관련 설정
* `scripts/` - 개발 및 운영 보조 스크립트
* `docs/` - 코드와 함께 관리할 필요가 있는 개발 문서

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