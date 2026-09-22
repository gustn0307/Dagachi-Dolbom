from fastapi import APIRouter, HTTPException, status

from app.schemas.activity_matching import (
    ActivityMatchingRequest,
    ActivityMatchingResponse,
)
from app.services.activity_matching_service import ActivityMatchingService


router = APIRouter(
    prefix="/internal/ai",
    tags=["AI"],
)


@router.post(
    "/activity-matching",
    response_model=ActivityMatchingResponse,
)
def match_activities(
    request: ActivityMatchingRequest,
) -> ActivityMatchingResponse:

    try:
        return ActivityMatchingService().match(request)

    except RuntimeError as exc:
        message = str(exc)

        if "인증" in message or "OPENAI_API_KEY" in message:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail=message,
            ) from exc

        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=message,
        ) from exc