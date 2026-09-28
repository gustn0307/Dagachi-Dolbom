"""
ActivityMatchingService 단위 테스트.

- 실제 OpenAI API를 호출하지 않고 client를 Mock으로 주입한다.
- OpenAI 정상 응답의 순위/후보 검증을 확인한다.
- 일시적 API 오류는 1회만 재시도하는지 확인한다.
- 응답 형식 오류는 OpenAI를 다시 호출하지 않는지 확인한다.

REQ-AI-15 : AI 활동 매칭 및 추천 순위 검증
REQ-AI-17 : AI 호출 장애/잘못된 응답 처리
"""

import json
from types import SimpleNamespace
from unittest.mock import MagicMock

import httpx
import pytest
from openai import (
    APIConnectionError,
    AuthenticationError,
    RateLimitError,
)

from app.core.config import get_settings
from app.services.activity_matching_service import ActivityMatchingService


# ------------------------------------------------------------------
# 테스트 헬퍼
# ------------------------------------------------------------------

def _fake_request() -> httpx.Request:
    return httpx.Request(
        "POST",
        "https://api.openai.com/v1/responses",
    )


def _fake_response(status_code: int) -> httpx.Response:
    return httpx.Response(
        status_code=status_code,
        request=_fake_request(),
    )


def _fake_openai_response(output_text: str):
    response = MagicMock()
    response.output_text = output_text
    return response


def _matching_request(*activity_ids: int):
    """
    ActivityMatchingService가 실제로 사용하는 요청 인터페이스만 가진 Mock입니다.

    match()에서는 model_dump_json(),
    _parse_response()에서는 candidates[*].activityId만 사용합니다.
    """
    request = MagicMock()

    request.candidates = [
        SimpleNamespace(activityId=activity_id)
        for activity_id in activity_ids
    ]

    request.model_dump_json.return_value = json.dumps(
        {
            "profile": {
                "completedActivityCount": 0,
                "experiencedRegions": [],
                "experiencedAgeGroups": [],
            },
            "candidates": [
                {"activityId": activity_id}
                for activity_id in activity_ids
            ],
        }
    )

    return request


def _valid_ai_json():
    return json.dumps(
        {
            "recommendations": [
                {
                    "activityId": 2,
                    "rank": 1,
                    "reason": "두 번째 활동을 우선 추천합니다.",
                },
                {
                    "activityId": 1,
                    "rank": 2,
                    "reason": "첫 번째 활동을 다음으로 추천합니다.",
                },
            ]
        },
        ensure_ascii=False,
    )


# ------------------------------------------------------------------
# REQ-AI-15 : 정상 응답 / 계약 검증
# ------------------------------------------------------------------

def test_req_ai_16_match_정상응답이면_rank순서로_정렬하고_reason과_model을_포함한다():
    client = MagicMock()

    client.responses.create.return_value = (
        _fake_openai_response(
            _valid_ai_json()
        )
    )

    service = ActivityMatchingService(
        client=client
    )

    request = _matching_request(
        1,
        2,
    )

    result = service.match(request)

    assert [
        recommendation.activityId
        for recommendation in result.recommendations
    ] == [2, 1]

    assert [
        recommendation.rank
        for recommendation in result.recommendations
    ] == [1, 2]

    assert result.recommendations[0].reason == (
        "두 번째 활동을 우선 추천합니다."
    )

    assert result.model == (
        get_settings().openai_model
    )

    client.responses.create.assert_called_once()


def test_match_JSON코드블록으로_감싸진_응답도_정상처리한다():
    client = MagicMock()

    fenced_json = (
        "```json\n"
        + _valid_ai_json()
        + "\n```"
    )

    client.responses.create.return_value = (
        _fake_openai_response(
            fenced_json
        )
    )

    service = ActivityMatchingService(
        client=client
    )

    result = service.match(
        _matching_request(1, 2)
    )

    assert [
        recommendation.activityId
        for recommendation in result.recommendations
    ] == [2, 1]


def test_req_ai_15_parse_response_요청후보가_누락되면_RuntimeError를_던진다():
    client = MagicMock()

    service = ActivityMatchingService(
        client=client
    )

    request = _matching_request(
        1,
        2,
    )

    output = json.dumps(
        {
            "recommendations": [
                {
                    "activityId": 1,
                    "rank": 1,
                    "reason": "추천 이유",
                }
            ]
        },
        ensure_ascii=False,
    )

    with pytest.raises(
        RuntimeError,
        match="후보가 요청과 일치하지 않습니다",
    ):
        service._parse_response(
            request,
            output,
        )


def test_req_ai_15_parse_response_요청에_없는_후보가_추가되면_RuntimeError를_던진다():
    client = MagicMock()

    service = ActivityMatchingService(
        client=client
    )

    request = _matching_request(
        1,
        2,
    )

    output = json.dumps(
        {
            "recommendations": [
                {
                    "activityId": 1,
                    "rank": 1,
                    "reason": "추천 이유 1",
                },
                {
                    "activityId": 2,
                    "rank": 2,
                    "reason": "추천 이유 2",
                },
                {
                    "activityId": 999,
                    "rank": 3,
                    "reason": "잘못 추가된 후보",
                },
            ]
        },
        ensure_ascii=False,
    )

    with pytest.raises(
        RuntimeError,
        match="후보가 요청과 일치하지 않습니다",
    ):
        service._parse_response(
            request,
            output,
        )


def test_parse_response_activityId가_중복되면_RuntimeError를_던진다():
    client = MagicMock()

    service = ActivityMatchingService(
        client=client
    )

    request = _matching_request(
        1,
        2,
    )

    output = json.dumps(
        {
            "recommendations": [
                {
                    "activityId": 1,
                    "rank": 1,
                    "reason": "추천 이유 1",
                },
                {
                    "activityId": 1,
                    "rank": 2,
                    "reason": "중복 후보",
                },
            ]
        },
        ensure_ascii=False,
    )

    # 현재 구현에서는 중복으로 인해 returned_ids가 요청 후보 집합과
    # 먼저 달라지므로 후보 불일치 RuntimeError가 발생한다.
    with pytest.raises(
        RuntimeError,
        match="후보가 요청과 일치하지 않습니다",
    ):
        service._parse_response(
            request,
            output,
        )


def test_parse_response_rank가_중복되면_RuntimeError를_던진다():
    client = MagicMock()

    service = ActivityMatchingService(
        client=client
    )

    request = _matching_request(
        1,
        2,
    )

    output = json.dumps(
        {
            "recommendations": [
                {
                    "activityId": 1,
                    "rank": 1,
                    "reason": "추천 이유 1",
                },
                {
                    "activityId": 2,
                    "rank": 1,
                    "reason": "추천 이유 2",
                },
            ]
        },
        ensure_ascii=False,
    )

    with pytest.raises(
        RuntimeError,
        match="순위가 올바르지 않습니다",
    ):
        service._parse_response(
            request,
            output,
        )


def test_parse_response_JSON이_깨져있으면_RuntimeError를_던진다():
    client = MagicMock()

    service = ActivityMatchingService(
        client=client
    )

    request = _matching_request(
        1,
        2,
    )

    with pytest.raises(
        RuntimeError,
        match="올바르지 않은 활동 매칭 결과",
    ):
        service._parse_response(
            request,
            "{잘못된 JSON",
        )


# ------------------------------------------------------------------
# REQ-AI-17 : OpenAI 장애 / retry
# ------------------------------------------------------------------

def test_match_일시적_연결실패후_두번째호출이_성공하면_정상반환한다():
    client = MagicMock()

    client.responses.create.side_effect = [
        APIConnectionError(
            request=_fake_request()
        ),
        _fake_openai_response(
            _valid_ai_json()
        ),
    ]

    service = ActivityMatchingService(
        client=client
    )

    result = service.match(
        _matching_request(1, 2)
    )

    assert [
        recommendation.activityId
        for recommendation in result.recommendations
    ] == [2, 1]

    # 최초 호출 실패 + 1회 재시도
    assert (
        client.responses.create.call_count
        == 2
    )


def test_match_일시적오류가_두번연속발생하면_RuntimeError를_던진다():
    client = MagicMock()

    client.responses.create.side_effect = [
        APIConnectionError(
            request=_fake_request()
        ),
        RateLimitError(
            "rate limited",
            response=_fake_response(429),
            body=None,
        ),
    ]

    service = ActivityMatchingService(
        client=client
    )

    with pytest.raises(
        RuntimeError,
        match="활동 매칭 호출에 실패했습니다",
    ):
        service.match(
            _matching_request(1, 2)
        )

    # 재시도는 정확히 한 번만 수행한다.
    assert (
        client.responses.create.call_count
        == 2
    )


def test_match_인증오류는_재시도하지않고_즉시실패한다():
    client = MagicMock()

    client.responses.create.side_effect = (
        AuthenticationError(
            "invalid api key",
            response=_fake_response(401),
            body=None,
        )
    )

    service = ActivityMatchingService(
        client=client
    )

    with pytest.raises(
        RuntimeError,
        match="인증에 실패했습니다",
    ):
        service.match(
            _matching_request(1, 2)
        )

    # 인증 문제는 재시도해도 해결되지 않으므로 1회만 호출한다.
    client.responses.create.assert_called_once()


def test_match_AI응답형식오류는_OpenAI를_재호출하지않는다():
    client = MagicMock()

    client.responses.create.return_value = (
        _fake_openai_response(
            "{잘못된 JSON"
        )
    )

    service = ActivityMatchingService(
        client=client
    )

    with pytest.raises(
        RuntimeError,
        match="올바르지 않은 활동 매칭 결과",
    ):
        service.match(
            _matching_request(1, 2)
        )

    # API 호출 자체는 성공했으므로
    # 응답 형식 오류 때문에 OpenAI를 다시 호출하면 안 된다.
    client.responses.create.assert_called_once()