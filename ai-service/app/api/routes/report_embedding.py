from fastapi import APIRouter, HTTPException, status

from app.schemas.report_embedding import (
    ReportEmbeddingRequest,
    ReportEmbeddingResponse,
)
from app.services.report_embedding_service import ReportEmbeddingService


router = APIRouter(
    prefix="/internal/ai",
    tags=["AI"],
)


@router.post(
    "/report-embedding",
    response_model=ReportEmbeddingResponse,
)
def create_report_embedding(
    request: ReportEmbeddingRequest,
) -> ReportEmbeddingResponse:
    """
    제보 원문으로 embedding 벡터를 생성합니다.

    Frontend에서는 직접 호출하지 않고
    Spring Boot가 내부적으로 호출합니다.
    """

    try:
        service = ReportEmbeddingService()

        return service.create_embedding(
            request.content
        )

    except RuntimeError as exc:
        message = str(exc)

        if "OPENAI_API_KEY" in message:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=message,
            ) from exc

        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=message,
        ) from exc