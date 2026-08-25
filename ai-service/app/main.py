from fastapi import FastAPI

# 각 기능별 API Router를 main 애플리케이션에 등록하기 위해 import합니다.
from app.api.routes.health import router as health_router

# 애플리케이션 공통 설정을 가져옵니다.
from app.core.config import get_settings

from app.api.routes.test import router as test_router

# .env 또는 기본값을 기반으로 설정 객체를 가져옵니다.
settings = get_settings()


# FastAPI 애플리케이션의 시작점입니다.
#
# title, version 값은 Swagger UI(/docs)와 OpenAPI 문서에 표시됩니다.
# 실제 AI 기능은 이 파일에 직접 작성하지 않고,
# api / services / schemas 등의 하위 모듈로 분리해서 관리합니다.
app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
)


# 서버 상태 확인용 health API를 FastAPI 애플리케이션에 등록합니다.
#
# 앞으로 AI 분석, 챗봇 등의 Router가 추가되면
# 같은 방식으로 Router를 추가합니다.
app.include_router(health_router)

# Spring Boot ↔ FastAPI 통신 확인용 테스트 API를 등록합니다.
#
# 이후 실제 AI 기능도 기능별 Router를 만들어
# 같은 방식으로 main 애플리케이션에 등록합니다.
app.include_router(test_router)