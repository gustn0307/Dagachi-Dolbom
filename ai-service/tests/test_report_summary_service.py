import unittest
from types import SimpleNamespace

from app.services.report_summary_service import ReportSummaryService


class FakeResponses:
    """
    OpenAI의 responses.create()를 대신하는 테스트용 객체입니다.

    실제 API 호출 없이 원하는 응답을 만들어 Service 로직을 검증합니다.
    """

    def __init__(self, output_text: str):
        self.output_text = output_text

    def create(self, **kwargs):
        return SimpleNamespace(
            output_text=self.output_text,
        )


class FakeOpenAIClient:
    """
    실제 OpenAI API를 호출하지 않는 테스트용 Client입니다.
    """

    def __init__(self, output_text: str):
        self.responses = FakeResponses(output_text)


class ReportSummaryServiceTest(unittest.TestCase):

    def test_summarize_returns_summary_and_model(self):
        # 정상적인 AI 응답을 가정합니다.
        client = FakeOpenAIClient(
            "어르신이 며칠째 보이지 않아 안부 확인이 필요합니다."
        )

        service = ReportSummaryService(client=client)

        result = service.summarize(
            "며칠째 어르신이 보이지 않고 집 앞에 신문이 쌓여 있습니다."
        )

        self.assertEqual(
            result.summary,
            "어르신이 며칠째 보이지 않아 안부 확인이 필요합니다.",
        )

        self.assertEqual(
            result.model,
            "gpt-4o-mini",
        )

    def test_summarize_raises_error_when_response_is_empty(self):
        # OpenAI 호출은 성공했지만 결과 텍스트가 비어 있는 상황을 가정합니다.
        client = FakeOpenAIClient("   ")

        service = ReportSummaryService(client=client)

        with self.assertRaises(RuntimeError) as context:
            service.summarize(
                "테스트 제보 내용입니다."
            )

        self.assertEqual(
            str(context.exception),
            "OpenAI가 빈 요약 결과를 반환했습니다.",
        )


if __name__ == "__main__":
    unittest.main()