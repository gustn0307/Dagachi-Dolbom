# Dagachi Dolbom AI Service

다같이돌봄 프로젝트의 AI 기능을 담당하는 FastAPI 서비스입니다.

현재 단계에서는 실제 OpenAI / RAG 기능을 구현하기 전,
Spring Boot와 FastAPI를 분리하여 운영하기 위한 공통 실행 구조와 설정을 구성합니다.

---

## 1. 기술 스택

- Python 3.12.x
- FastAPI
- Uvicorn
- Pydantic
- Pydantic Settings

향후 AI 기능 구현 과정에서 필요에 따라 다음 기술이 추가될 수 있습니다.

- OpenAI API
- RAG
- Vector DB
- Embedding
- AI Agent

현재 사용하지 않는 AI 라이브러리는 미리 설치하지 않습니다.

---

## 2. Python 버전

이 프로젝트의 AI 서비스는 다음 Python 버전을 기준으로 개발합니다.

    Python 3.12.x

개발 환경마다 patch 버전은 달라도 됩니다.

예:

    Python 3.12.9
    Python 3.12.10

Python 3.13, 3.14 등 다른 major/minor 버전으로 가상환경을 생성하지 않도록 주의합니다.

Python 버전 확인:

    python --version

예상 결과:

    Python 3.12.x

---

## 3. 가상환경 생성

`ai-service` 디렉터리에서 Python 3.12로 가상환경을 생성합니다.

Windows 예시:

    cd ai-service
    py -3.12 -m venv .venv

PC에서 `py -3.12` 명령이 동작하지 않는 경우 설치된 Python 3.12 경로를 직접 사용할 수 있습니다.

예:

    & "C:\Program Files\Python312\python.exe" -m venv .venv

가상환경 활성화:

    .\.venv\Scripts\Activate.ps1

활성화 후 반드시 Python 버전을 확인합니다.

    python --version

`.venv` 디렉터리는 Git에 커밋하지 않습니다.

---

## 4. Python 패키지 설치

가상환경이 활성화된 상태에서 실행합니다.

    pip install -r requirements.txt

현재 주요 직접 의존성은 다음과 같습니다.

- FastAPI
- Uvicorn
- Pydantic
- Pydantic Settings

`requirements.txt`에는 개발 환경 재현을 위해 하위 의존성까지 버전이 고정되어 있을 수 있습니다.

---

## 5. 환경변수 설정

환경변수 예시는 다음 파일에서 관리합니다.

    .env.example

실제 로컬 환경에서는 `.env.example`을 참고하여 `.env`를 작성합니다.

현재 환경변수:

    APP_NAME=Dagachi Dolbom AI Service
    APP_VERSION=0.1.0
    OPENAI_API_KEY=

주의:

- `.env.example`에는 실제 비밀값을 작성하지 않습니다.
- 실제 OpenAI API Key는 `.env` 또는 운영 환경변수에 설정합니다.
- `.env`는 Git에 커밋하지 않습니다.
- API Key, 비밀번호 등의 비밀정보를 소스코드에 직접 작성하지 않습니다.

현재는 실제 OpenAI 기능을 구현하지 않았으므로 `OPENAI_API_KEY`가 비어 있어도 서비스 실행이 가능합니다.

---

## 6. FastAPI 실행

`ai-service` 디렉터리에서 가상환경을 활성화한 후 실행합니다.

    uvicorn app.main:app --reload

기본 실행 주소:

    http://localhost:8000

Swagger UI:

    http://localhost:8000/docs

Health Check:

    http://localhost:8000/health

정상 응답:

    {
      "status": "ok"
    }

`/health`는 AI 서비스 프로세스가 정상 실행 중인지 확인하기 위한 liveness endpoint입니다.

향후 DB, Vector DB, 외부 AI API 등의 연결 상태까지 검사해야 한다면 별도의 readiness endpoint를 추가합니다.

---

## 7. 현재 프로젝트 구조

    ai-service/
    ├─ app/
    │  ├─ __init__.py
    │  ├─ main.py
    │  │
    │  ├─ api/
    │  │  ├─ __init__.py
    │  │  └─ routes/
    │  │     ├─ __init__.py
    │  │     └─ health.py
    │  │
    │  ├─ core/
    │  │  ├─ __init__.py
    │  │  └─ config.py
    │  │
    │  ├─ schemas/
    │  │  └─ __init__.py
    │  │
    │  └─ services/
    │     └─ __init__.py
    │
    ├─ .env
    ├─ .env.example
    ├─ requirements.txt
    └─ README.md

각 디렉터리 역할:

### `api/routes`

FastAPI HTTP endpoint를 정의합니다.

Spring Boot의 Controller와 비슷한 역할입니다.

예:

    health.py
    chat.py
    analysis.py

### `schemas`

HTTP 요청/응답 데이터 구조를 정의합니다.

Spring Boot의 Request DTO / Response DTO와 비슷한 역할입니다.

Pydantic `BaseModel`을 사용합니다.

### `services`

실제 AI 비즈니스 로직을 구현합니다.

예:

- OpenAI 호출
- RAG 검색
- 문서 분석
- 프롬프트 처리
- AI 응답 생성

Spring Boot의 Service 계층과 비슷한 역할입니다.

### `core`

서비스 전체에서 공통으로 사용하는 설정을 관리합니다.

현재는 `pydantic-settings`를 사용하여 환경변수를 관리합니다.

향후 다음과 같은 공통 요소도 필요에 따라 위치시킬 수 있습니다.

- OpenAI Client
- Vector DB Client
- 공통 AI 설정

### `main.py`

FastAPI 애플리케이션을 생성하고 router를 연결하는 시작점입니다.

---

## 8. Spring Boot와 FastAPI 역할 분리

다같이돌봄 프로젝트에서는 브라우저가 FastAPI를 직접 호출하지 않는 구조를 기본으로 합니다.

    React
      ↓
    Spring Boot :8080
      ↓
    FastAPI :8000
      ↓
    OpenAI / RAG / Vector DB

각 서비스의 역할은 다음과 같습니다.

### React

- 화면
- 사용자 입력
- 결과 출력

### Spring Boot

- 인증 / 인가
- JWT
- 사용자 및 서비스 데이터
- DB 처리
- 비즈니스 흐름 제어
- FastAPI 호출

### FastAPI

- AI 처리
- 자연어 처리
- OpenAI 호출
- RAG
- Vector 검색
- AI Agent

따라서 React에서 OpenAI API나 FastAPI를 직접 호출하지 않습니다.

---

## 9. Spring Boot → FastAPI 설정

Spring Boot의 `application.yaml`에서 FastAPI 주소와 timeout을 관리합니다.

    ai:
      service:
        base-url: ${AI_SERVICE_BASE_URL:http://localhost:8000}
        connect-timeout: ${AI_SERVICE_CONNECT_TIMEOUT:3000}
        read-timeout: ${AI_SERVICE_READ_TIMEOUT:30000}

### `AI_SERVICE_BASE_URL`

FastAPI 서비스 주소입니다.

로컬 기본값:

    http://localhost:8000

배포 환경에서는 환경변수로 변경합니다.

Docker Compose 환경에서는 예를 들어 다음과 같은 서비스 DNS를 사용할 수 있습니다.

    http://ai-service:8000

### `AI_SERVICE_CONNECT_TIMEOUT`

FastAPI 서버와 연결을 맺을 때 기다리는 최대 시간입니다.

기본값:

    3000ms

### `AI_SERVICE_READ_TIMEOUT`

FastAPI 연결 후 응답을 기다리는 최대 시간입니다.

기본값:

    30000ms

AI 요청은 일반 CRUD API보다 오래 걸릴 가능성이 있어 connect timeout보다 긴 값을 사용합니다.

---

## 10. Spring Boot RestClient

Spring Boot에서는 FastAPI 호출을 위한 공통 `RestClient` Bean을 사용합니다.

관련 패키지:

    backend/src/main/java/com/dagachi/backend/common/ai/

현재 공통 구조:

    common/ai/
    ├─ client/
    │  └─ AiServiceClient.java
    └─ config/
       └─ AiServiceConfig.java

`AiServiceConfig`에서는 다음을 공통 설정합니다.

- FastAPI base URL
- connect timeout
- read timeout

실제 AI 기능을 구현할 때 `AiServiceClient`에 필요한 호출 메서드를 추가합니다.

예:

    analyzeReport(...)
    chat(...)
    searchKnowledge(...)

임시 통신 확인을 위해 사용했던 `test/echo` API는 공통 골격 검증 후 제거했습니다.

---

## 11. 향후 AI 기능 구현 전 확인할 사항

실제 AI API를 추가하기 전에 다음 공통 정책을 결정합니다.

- FastAPI 연결 실패 처리
- connect/read timeout 처리
- FastAPI 4xx/5xx 응답 변환
- 응답 역직렬화 실패 처리
- Spring 공통 ErrorCode
- 로그 및 trace/correlation ID
- AI 요청 재시도 정책

특히 AI 요청은 동일 요청을 다시 실행하면 비용이 중복 발생하거나 결과가 달라질 수 있으므로 무조건적인 자동 재시도는 사용하지 않습니다.

---

## 12. Git에 포함하지 않는 파일

다음 파일은 Git에 커밋하지 않습니다.

    .env
    .venv/
    __pycache__/
    *.pyc

Git에 포함하는 환경변수 문서는 다음 파일입니다.

    .env.example

`.env.example`에는 환경변수 이름만 관리하고 실제 비밀값은 작성하지 않습니다.
