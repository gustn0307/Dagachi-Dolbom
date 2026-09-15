from fastapi import APIRouter, HTTPException, status

from app.schemas.report_title import (
    ReportTitleRequest,
    ReportTitleResponse,
)
from app.services.report_title_service import ReportTitleService


# Spring Boot가 내부적으로 호출하는 AI API Router입니다.
# Frontend에서는 이 Endpoint를 직접 호출하지 않습니다.
#
# REPORT_SUMMARY(상세 화면 문단 요약)와는 완전히 분리된
# 별도의 AI 분석(REPORT_TITLE, 목록 화면 한줄 제목)입니다.
router = APIRouter(
    prefix="/internal/ai",
    tags=["AI"],
)


@router.post(
    "/report-title",
    response_model=ReportTitleResponse,
)
def create_report_title(
    request: ReportTitleRequest,
) -> ReportTitleResponse:
    """
    제보 원문을 AI로 목록용 한줄 제목으로 정리합니다.

    Spring Boot가 제보 원문을 전달하면
    AI Service가 제목 결과와 사용 모델명을 반환합니다.
    """

    try:
        service = ReportTitleService()

        return service.create_title(request.content)

    except RuntimeError as exc:
        message = str(exc)

        # API Key 자체가 설정되지 않은 경우에는
        # AI 기능을 사용할 수 없는 서버 설정 문제로 처리합니다.
        if "OPENAI_API_KEY" in message:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=message,
            ) from exc

        # OpenAI 외부 API 호출 실패나 빈 응답 등은
        # Backend Gateway 계열 오류로 변환합니다.
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=message,
        ) from exc