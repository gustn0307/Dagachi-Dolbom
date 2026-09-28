"""
ReportTitleService 단위 테스트.

- 실제 OpenAI API를 호출하지 않고 client를 Mock으로 주입해서 검증한다.
- openai 패키지의 예외(AuthenticationError/RateLimitError/APIConnectionError/APIError)는
  openai-python 1.x 기준 생성자 시그니처(APIStatusError는 response=, APIError/
  APIConnectionError는 request=)를 사용한다. 프로젝트의 openai 버전이 다르면
  아래 _fake_response()/예외 생성 부분을 버전에 맞게 조정해야 한다.
"""
from unittest.mock import MagicMock

import httpx
import pytest
from openai import APIConnectionError, APIError, AuthenticationError, RateLimitError

from app.core.config import get_settings
from app.services.report_title_service import ReportTitleService


def _fake_request() -> httpx.Request:
    return httpx.Request("POST", "https://api.openai.com/v1/responses")


def _fake_response(status_code: int) -> httpx.Response:
    return httpx.Response(status_code=status_code, request=_fake_request())


def _fake_openai_response(output_text: str):
    response = MagicMock()
    response.output_text = output_text
    return response


def test_req_ai_12_create_title_정상_응답이면_공백을_제거한_제목과_모델명을_반환한다():
    client = MagicMock()
    client.responses.create.return_value = _fake_openai_response("  독거노인 낙상 의심 제보  ")

    service = ReportTitleService(client=client)
    result = service.create_title("어제 골목에서 넘어진 것 같다는 이웃 제보입니다.")

    assert result.title == "독거노인 낙상 의심 제보"
    assert result.model == get_settings().openai_model
    client.responses.create.assert_called_once()


def test_req_ai_12_create_title_빈_제목을_반환하면_RuntimeError를_던진다():
    client = MagicMock()
    client.responses.create.return_value = _fake_openai_response("   ")

    service = ReportTitleService(client=client)

    with pytest.raises(RuntimeError, match="빈 제목"):
        service.create_title("내용")


def test_req_ai_12_create_title_인증에_실패하면_RuntimeError로_변환한다():
    client = MagicMock()
    client.responses.create.side_effect = AuthenticationError(
        "invalid api key", response=_fake_response(401), body=None
    )

    service = ReportTitleService(client=client)

    with pytest.raises(RuntimeError, match="인증"):
        service.create_title("내용")


def test_req_ai_12_create_title_요청_한도를_초과하면_RuntimeError로_변환한다():
    client = MagicMock()
    client.responses.create.side_effect = RateLimitError(
        "rate limited", response=_fake_response(429), body=None
    )

    service = ReportTitleService(client=client)

    with pytest.raises(RuntimeError, match="한도"):
        service.create_title("내용")


def test_req_ai_12_create_title_연결에_실패하면_RuntimeError로_변환한다():
    client = MagicMock()
    client.responses.create.side_effect = APIConnectionError(request=_fake_request())

    service = ReportTitleService(client=client)

    with pytest.raises(RuntimeError, match="연결"):
        service.create_title("내용")


def test_req_ai_12_create_title_그_외_OpenAI_오류는_RuntimeError로_변환한다():
    client = MagicMock()
    client.responses.create.side_effect = APIError("unknown error", request=_fake_request(), body=None)

    service = ReportTitleService(client=client)

    with pytest.raises(RuntimeError, match="호출에 실패"):
        service.create_title("내용")


def test_req_ai_12_create_title_요청_content를_그대로_input으로_전달한다():
    client = MagicMock()
    client.responses.create.return_value = _fake_openai_response("제목")

    service = ReportTitleService(client=client)
    service.create_title("원문 그대로 전달되어야 하는 제보 내용")

    _, kwargs = client.responses.create.call_args
    assert kwargs["input"] == "원문 그대로 전달되어야 하는 제보 내용"
    assert kwargs["model"] == get_settings().openai_model