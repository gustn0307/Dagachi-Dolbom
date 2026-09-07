from fastapi import FastAPI

# 각 기능별 API Router를 main 애플리케이션에 등록하기 위해 import합니다.
from app.api.routes.health import router as health_router
from app.api.routes.report_summary import router as report_summary_router

# 애플리케이션 공통 설정을 가져옵니다.
from app.core.config import get_settings

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


# 서버 상태 확인용 health API를 등록합니다.
app.include_router(health_router)

# Spring Boot가 내부적으로 호출할 제보 AI 요약 API를 등록합니다.
app.include_router(report_summary_router)