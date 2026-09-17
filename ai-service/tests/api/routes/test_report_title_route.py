"""
POST /internal/ai/report-title 라우터 테스트.

라우터가 ReportTitleService()를 Depends 없이 직접 생성하므로,
app.api.routes.report_title.ReportTitleService를 patch해서 대체한다.
(main.py의 `from app.api.routes.report_title import router as report_title_router`
기준으로 모듈 경로를 추정했다. 실제 파일 경로가 다르면 patch 대상 문자열을 맞춰야 한다.)
"""
from unittest.mock import patch

from fastapi.testclient import TestClient

from app.main import app
from app.schemas.report_title import ReportTitleResponse

client = TestClient(app)

ENDPOINT = "/internal/ai/report-title"
PATCH_TARGET = "app.api.routes.report_title.ReportTitleService"


def test_report_title_정상요청이면_200과_제목모델을_반환한다():
    with patch(PATCH_TARGET) as mock_service_class:
        mock_service_class.return_value.create_title.return_value = ReportTitleResponse(
            title="독거노인 안전 확인 요청", model="gpt-4o-mini"
        )

        response = client.post(
            ENDPOINT,
            json={"content": "혼자 사시는 어르신이 며칠째 인기척이 없다는 제보입니다."},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["title"] == "독거노인 안전 확인 요청"
    assert body["model"] == "gpt-4o-mini"


def test_report_title_content가_비어있으면_422를_반환한다():
    response = client.post(ENDPOINT, json={"content": ""})

    assert response.status_code == 422


def test_report_title_content가_5000자를_초과하면_422를_반환한다():
    response = client.post(ENDPOINT, json={"content": "가" * 5001})

    assert response.status_code == 422


def test_report_title_content_필드가_없으면_422를_반환한다():
    response = client.post(ENDPOINT, json={})

    assert response.status_code == 422


def test_report_title_API_KEY가_설정되지_않았으면_503을_반환한다():
    with patch(PATCH_TARGET) as mock_service_class:
        mock_service_class.return_value.create_title.side_effect = RuntimeError(
            "OPENAI_API_KEY가 설정되지 않았습니다."
        )

        response = client.post(ENDPOINT, json={"content": "제보 원문"})

    assert response.status_code == 503
    assert "OPENAI_API_KEY" in response.json()["detail"]


def test_report_title_그_외_OpenAI_오류는_502를_반환한다():
    with patch(PATCH_TARGET) as mock_service_class:
        mock_service_class.return_value.create_title.side_effect = RuntimeError(
            "OpenAI 인증에 실패했습니다."
        )

        response = client.post(ENDPOINT, json={"content": "제보 원문"})

    assert response.status_code == 502
    assert "인증" in response.json()["detail"]