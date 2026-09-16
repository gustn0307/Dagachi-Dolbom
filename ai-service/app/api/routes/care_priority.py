from fastapi import APIRouter, HTTPException, status

from app.schemas.care_priority import CarePriorityRequest, CarePriorityResponse
from app.services.care_priority_service import CarePriorityService

router = APIRouter(prefix="/internal/ai", tags=["AI"])


@router.post("/care-priority", response_model=CarePriorityResponse)
def analyze_care_priority(request: CarePriorityRequest) -> CarePriorityResponse:
    try:
        return CarePriorityService().analyze(request)
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
