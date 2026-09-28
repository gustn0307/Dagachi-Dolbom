from pydantic import BaseModel, Field


# 이용자가 과거에 수행한 활동 경험을 AI에 전달합니다.
class UserMatchingProfile(BaseModel):
    completedActivityCount: int = Field(..., ge=0)
    experiencedRegions: list[str]
    experiencedAgeGroups: list[str]


# 추천 대상자의 최근 승인된 활동 기록입니다.
class CandidateHistory(BaseModel):
    daysAgo: int = Field(..., ge=0)
    visitResult: str
    checklist: dict[str, str] | None = None


# Spring이 1차 필터링한 추천 후보 활동입니다.
class ActivityMatchingCandidate(BaseModel):
    activityId: int
    distanceKm: float | None = Field(default=None, ge=0)
    daysSinceLastChecked: int | None = Field(default=None, ge=0)
    ageGroup: str
    region: str
    requiredPeople: int = Field(..., ge=1)
    approvedCount: int = Field(..., ge=0)
    recentRecords: list[CandidateHistory] = Field(
        default_factory=list,
        max_length=3,
    )


# Spring → FastAPI 요청입니다.
class ActivityMatchingRequest(BaseModel):
    profile: UserMatchingProfile
    candidates: list[ActivityMatchingCandidate] = Field(
        ...,
        min_length=1,
        max_length=10,
    )


# AI가 각 활동에 부여한 추천 순위와 이유입니다.
class ActivityMatchingRecommendation(BaseModel):
    activityId: int
    rank: int = Field(..., ge=1, le=10)
    reason: str = Field(..., min_length=1, max_length=300)


# FastAPI → Spring 응답입니다.
class ActivityMatchingResponse(BaseModel):
    recommendations: list[ActivityMatchingRecommendation]
    model: str