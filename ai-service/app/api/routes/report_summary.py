from fastapi import APIRouter, HTTPException, status

from app.schemas.report_summary import (
    ReportSummaryRequest,
    ReportSummaryResponse,
)
from app.services.report_summary_service import ReportSummaryService


# Spring Boot가 내부적으로 호출하는 AI API Router입니다.
# Frontend에서는 이 Endpoint를 직접 호출하지 않습니다.
router = APIRouter(
    prefix="/internal/ai",
    tags=["AI"],
)


@router.post(
    "/report-summary",
    response_model=ReportSummaryResponse,
)
def summarize_report(
    request: ReportSummaryRequest,
) -> ReportSummaryResponse:
    """
    제보 원문을 AI로 요약합니다.

    Spring Boot가 제보 원문을 전달하면
    AI Service가 요약 결과와 사용 모델명을 반환합니다.
    """

    try:
        service = ReportSummaryService()

        return service.summarize(request.content)

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