import unittest
from unittest.mock import MagicMock, patch

from fastapi import HTTPException

from app.api.routes.report_summary import summarize_report
from app.schemas.report_summary import ReportSummaryRequest


class ReportSummaryRouteTest(unittest.TestCase):

    @patch(
        "app.api.routes.report_summary.ReportSummaryService"
    )
    def test_returns_503_when_api_key_is_missing(
        self,
        mock_service_class,
    ):
        """
        OPENAI_API_KEY가 없을 때 Router가 503으로 변환하는지 확인합니다.
        """

        # Service 생성 단계에서 API Key 누락 오류가 발생하는 상황입니다.
        mock_service_class.side_effect = RuntimeError(
            "OPENAI_API_KEY가 설정되지 않았습니다."
        )

        request = ReportSummaryRequest(
            content="테스트 제보 내용입니다."
        )

        with self.assertRaises(HTTPException) as context:
            summarize_report(request)

        self.assertEqual(
            context.exception.status_code,
            503,
        )

        self.assertEqual(
            context.exception.detail,
            "OPENAI_API_KEY가 설정되지 않았습니다.",
        )

    @patch(
        "app.api.routes.report_summary.ReportSummaryService"
    )
    def test_returns_502_when_openai_call_fails(
        self,
        mock_service_class,
    ):
        """
        OpenAI 호출 오류를 Router가 502로 변환하는지 확인합니다.
        """

        mock_service = MagicMock()

        # Service는 생성됐지만 실제 summarize 호출에서 실패하는 상황입니다.
        mock_service.summarize.side_effect = RuntimeError(
            "OpenAI 호출에 실패했습니다."
        )

        mock_service_class.return_value = mock_service

        request = ReportSummaryRequest(
            content="테스트 제보 내용입니다."
        )

        with self.assertRaises(HTTPException) as context:
            summarize_report(request)

        self.assertEqual(
            context.exception.status_code,
            502,
        )

        self.assertEqual(
            context.exception.detail,
            "OpenAI 호출에 실패했습니다.",
        )


if __name__ == "__main__":
    unittest.main()