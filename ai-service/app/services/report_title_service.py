from openai import (
    APIConnectionError,
    APIError,
    AuthenticationError,
    OpenAI,
    RateLimitError,
)

from app.core.config import get_settings
from app.core.openai_client import get_openai_client
from app.schemas.report_title import ReportTitleResponse


class ReportTitleService:
    """
    제보 원문을 OpenAI에 전달하여
    목록 화면에 표시할 한줄 제목만 생성하는 Service입니다.

    REPORT_SUMMARY와는 완전히 별도의 OpenAI 호출이며,
    서로의 성공/실패에 영향을 주지 않습니다.
    """

    def __init__(
        self,
        client: OpenAI | None = None,
    ) -> None:
        self.settings = get_settings()
        self.client = client or get_openai_client()

    def create_title(
        self,
        content: str,
    ) -> ReportTitleResponse:
        try:
            response = self.client.responses.create(
                model=self.settings.openai_model,
                instructions=(
                    "당신은 지역사회 돌봄 서비스의 제보 내용을 "
                    "목록 화면에서 한눈에 볼 수 있는 제목으로 정리하는 도우미입니다. "
                    "제보 원문에 명시된 사실만 사용하고, 추측하거나 새로운 정보를 만들지 마세요. "
                    "관찰된 사실을 원인이나 결과로 해석하지 마세요. "
                    "제보자의 추측이나 우려가 포함되어 있더라도 그것을 사실로 단정하지 마세요. "
                    "의료적 진단이나 위험도를 판단하지 마세요. "
                    "핵심 상황을 압축한 한국어 명사형 문구로, 공백 포함 20자 내외로 작성하세요. "
                    "\"~합니다\", \"~보입니다\" 같은 문장형 어미로 끝내지 말고 제목처럼 작성하세요. "
                    "다른 설명 없이 제목 문구 하나만 출력하세요."
                ),
                input=content,
            )

        except AuthenticationError as exc:
            raise RuntimeError("OpenAI 인증에 실패했습니다.") from exc
        except RateLimitError as exc:
            raise RuntimeError("OpenAI 요청 한도를 초과했습니다.") from exc
        except APIConnectionError as exc:
            raise RuntimeError("OpenAI 서버에 연결할 수 없습니다.") from exc
        except APIError as exc:
            raise RuntimeError("OpenAI 호출에 실패했습니다.") from exc

        title = response.output_text.strip()

        if not title:
            raise RuntimeError("OpenAI가 빈 제목 결과를 반환했습니다.")

        return ReportTitleResponse(
            title=title,
            model=self.settings.openai_model,
        )