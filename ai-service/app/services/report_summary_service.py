from openai import (
    APIConnectionError,
    APIError,
    AuthenticationError,
    OpenAI,
    RateLimitError,
)

from app.core.config import get_settings
from app.core.openai_client import get_openai_client
from app.schemas.report_summary import ReportSummaryResponse


class ReportSummaryService:
    """
    제보 원문을 OpenAI에 전달하여 요약 결과를 생성하는 Service입니다.

    Router에서는 HTTP 요청/응답만 담당하고,
    실제 AI 호출과 결과 검증은 이 Service에서 처리합니다.
    """

    def __init__(
        self,
        client: OpenAI | None = None,
    ) -> None:
        self.settings = get_settings()

        # 실제 실행에서는 공통 OpenAI Client를 사용합니다.
        # 테스트에서는 Fake Client를 주입할 수 있도록 구성합니다.
        self.client = client or get_openai_client()

    def summarize(
        self,
        content: str,
    ) -> ReportSummaryResponse:
        """
        제보 원문을 요약하여 summary와 model 정보를 반환합니다.
        """

        try:
            response = self.client.responses.create(
                model=self.settings.openai_model,

                # 요약의 역할과 안전 범위를 Prompt로 제한합니다.
                # 원문에 없는 사실을 생성하거나 의료적 판단을 하지 않도록 합니다.
                instructions=(
                    "당신은 지역사회 돌봄 서비스의 제보 내용을 "
                    "기관 담당자가 빠르게 파악할 수 있도록 요약하는 도우미입니다. "
                    "제보 원문에 명시된 사실만 사용하고, 추측하거나 새로운 정보를 만들지 마세요. "
                    "관찰된 사실을 원인이나 결과로 해석하지 마세요. "
                    "제보자의 추측이나 우려가 포함되어 있더라도 그것을 사실로 단정하지 마세요. "
                    "의료적 진단이나 위험도를 판단하지 마세요. "
                    "반복되는 표현과 부수적인 설명은 제거하고, "
                    "기관 담당자가 확인해야 할 핵심 상황만 남기세요. "
                    "결과는 한국어 1~2문장으로 작성하고 약 100자 이내로 간결하게 요약하세요."
                ),

                # 첫 구현에서는 Report.content만 AI 입력으로 전달합니다.
                input=content,
            )

        # OpenAI 인증 실패
        except AuthenticationError as exc:
            raise RuntimeError(
                "OpenAI 인증에 실패했습니다."
            ) from exc

        # OpenAI 요청 한도 초과
        except RateLimitError as exc:
            raise RuntimeError(
                "OpenAI 요청 한도를 초과했습니다."
            ) from exc

        # OpenAI 서버와의 네트워크 연결 실패
        except APIConnectionError as exc:
            raise RuntimeError(
                "OpenAI 서버에 연결할 수 없습니다."
            ) from exc

        # 그 밖의 OpenAI API 오류
        except APIError as exc:
            raise RuntimeError(
                "OpenAI 호출에 실패했습니다."
            ) from exc

        # SDK 응답은 정상이어도 실제 텍스트가 비어 있을 수 있으므로 확인합니다.
        summary = response.output_text.strip()

        if not summary:
            raise RuntimeError(
                "OpenAI가 빈 요약 결과를 반환했습니다."
            )

        return ReportSummaryResponse(
            summary=summary,
            model=self.settings.openai_model,
        )