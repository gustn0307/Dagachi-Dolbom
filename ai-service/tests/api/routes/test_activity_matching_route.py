"""
POST /internal/ai/activity-matching 라우터 테스트.

- 실제 OpenAI API를 호출하지 않는다.
- ActivityMatchingService를 patch해서 HTTP 계약만 검증한다.
- 정상 요청, Pydantic validation, AI 서비스 오류의 HTTP 변환을 확인한다.

REQ-AI-15 : AI 활동 매칭 정상 응답
REQ-AI-17 : AI 장애 시 HTTP 오류 변환
"""

from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import app
from app.schemas.activity_matching import ActivityMatchingResponse


client = TestClient(app)

ENDPOINT = "/internal/ai/activity-matching"
PATCH_TARGET = (
    "app.api.routes.activity_matching.ActivityMatchingService"
)


# ------------------------------------------------------------------
# 테스트 요청 데이터
# ------------------------------------------------------------------

def _valid_request():
    return {
        "profile": {
            "completedActivityCount": 2,
            "experiencedRegions": [
                "강남구",
            ],
            "experiencedAgeGroups": [
                "70대",
            ],
        },
        "candidates": [
            {
                "activityId": 1,
                "distanceKm": 1.2,
                "daysSinceLastChecked": 10,
                "ageGroup": "80대",
                "region": "강남구",
                "requiredPeople": 2,
                "approvedCount": 1,
                "recentRecords": [],
            },
            {
                "activityId": 2,
                "distanceKm": 2.5,
                "daysSinceLastChecked": 30,
                "ageGroup": "70대",
                "region": "송파구",
                "requiredPeople": 2,
                "approvedCount": 0,
                "recentRecords": [],
            },
        ],
    }


# ------------------------------------------------------------------
# REQ-AI-15 : 정상 요청 / Pydantic validation
# ------------------------------------------------------------------

def test_activity_matching_정상요청이면_200과_추천결과를_반환한다():

    with patch(
        PATCH_TARGET
    ) as mock_service_class:

        mock_service_class.return_value.match.return_value = (
            ActivityMatchingResponse.model_validate(
                {
                    "recommendations": [
                        {
                            "activityId": 2,
                            "rank": 1,
                            "reason": (
                                "최근 안부 확인 경과 기간을 "
                                "고려해 우선 추천합니다."
                            ),
                        },
                        {
                            "activityId": 1,
                            "rank": 2,
                            "reason": (
                                "거리와 활동 경험을 "
                                "고려해 추천합니다."
                            ),
                        },
                    ],
                    "model": "gpt-4o-mini",
                }
            )
        )

        response = client.post(
            ENDPOINT,
            json=_valid_request(),
        )

    assert response.status_code == 200

    body = response.json()

    assert body["model"] == "gpt-4o-mini"

    assert len(
        body["recommendations"]
    ) == 2

    assert (
        body["recommendations"][0]["activityId"]
        == 2
    )

    assert (
        body["recommendations"][0]["rank"]
        == 1
    )


def test_activity_matching_candidates가_없으면_422를_반환한다():

    request = _valid_request()

    request["candidates"] = []

    response = client.post(
        ENDPOINT,
        json=request,
    )

    assert response.status_code == 422


def test_activity_matching_candidates가_10건을_초과하면_422를_반환한다():

    request = _valid_request()

    request["candidates"] = [
        {
            "activityId": activity_id,
            "distanceKm": 1.0,
            "daysSinceLastChecked": 10,
            "ageGroup": "80대",
            "region": "강남구",
            "requiredPeople": 2,
            "approvedCount": 0,
            "recentRecords": [],
        }
        for activity_id in range(1, 12)
    ]

    response = client.post(
        ENDPOINT,
        json=request,
    )

    assert response.status_code == 422


def test_activity_matching_activityId가_없으면_422를_반환한다():

    request = _valid_request()

    del request["candidates"][0]["activityId"]

    response = client.post(
        ENDPOINT,
        json=request,
    )

    assert response.status_code == 422


# ------------------------------------------------------------------
# REQ-AI-17 : AI 오류 → HTTP 상태 변환
# ------------------------------------------------------------------

def test_activity_matching_API_KEY가_설정되지_않으면_503을_반환한다():

    with patch(
        PATCH_TARGET
    ) as mock_service_class:

        mock_service_class.return_value.match.side_effect = (
            RuntimeError(
                "OPENAI_API_KEY가 설정되지 않았습니다."
            )
        )

        response = client.post(
            ENDPOINT,
            json=_valid_request(),
        )

    assert response.status_code == 503

    assert (
        "OPENAI_API_KEY"
        in response.json()["detail"]
    )


def test_activity_matching_OpenAI호출실패는_502를_반환한다():

    with patch(
        PATCH_TARGET
    ) as mock_service_class:

        mock_service_class.return_value.match.side_effect = (
            RuntimeError(
                "OpenAI 활동 매칭 호출에 실패했습니다."
            )
        )

        response = client.post(
            ENDPOINT,
            json=_valid_request(),
        )

    assert response.status_code == 502

    assert (
        "활동 매칭 호출에 실패"
        in response.json()["detail"]
    )


def test_activity_matching_AI응답형식오류도_502를_반환한다():

    with patch(
        PATCH_TARGET
    ) as mock_service_class:

        mock_service_class.return_value.match.side_effect = (
            RuntimeError(
                "OpenAI가 올바르지 않은 "
                "활동 매칭 결과를 반환했습니다."
            )
        )

        response = client.post(
            ENDPOINT,
            json=_valid_request(),
        )

    assert response.status_code == 502

    assert (
        "올바르지 않은 활동 매칭 결과"
        in response.json()["detail"]
    )