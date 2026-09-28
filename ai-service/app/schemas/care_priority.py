from pydantic import BaseModel, Field


class CarePriorityCandidate(BaseModel):
    candidateKey: str = Field(..., min_length=1, max_length=100)
    daysSinceLastCheck: int | None = Field(default=None, ge=0)
    recentActivityCount: int = Field(..., ge=0)
    mealConcernCount: int = Field(..., ge=0)
    healthConcernCount: int = Field(..., ge=0)
    supportNeededCount: int = Field(..., ge=0)
    hasUpcomingActivity: bool


class CarePriorityRequest(BaseModel):
    candidates: list[CarePriorityCandidate] = Field(..., min_length=1, max_length=10)


class CarePriorityRecommendation(BaseModel):
    candidateKey: str
    riskLevel: str
    score: int = Field(..., ge=0, le=100)
    reasons: list[str] = Field(..., min_length=1, max_length=3)
    recommendedAction: str


class CarePriorityResponse(BaseModel):
    recommendations: list[CarePriorityRecommendation]
    model: str
