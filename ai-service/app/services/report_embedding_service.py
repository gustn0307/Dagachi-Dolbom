from openai import (
    APIConnectionError,
    APIError,
    AuthenticationError,
    OpenAI,
    RateLimitError,
)

from app.core.config import get_settings
from app.core.openai_client import get_openai_client
from app.schemas.report_embedding import ReportEmbeddingResponse


class ReportEmbeddingService:
    """
    제보 원문을 OpenAI embedding API에 전달하여
    벡터를 생성하는 Service입니다.
    """

    def __init__(
        self,
        client: OpenAI | None = None,
    ) -> None:
        self.settings = get_settings()
        self.client = client or get_openai_client()

    def create_embedding(
        self,
        content: str,
    ) -> ReportEmbeddingResponse:
        """
        제보 원문으로 embedding 벡터를 생성합니다.
        """

        try:
            response = self.client.embeddings.create(
                model=self.settings.openai_embedding_model,
                input=content,
            )

        except AuthenticationError as exc:
            raise RuntimeError(
                "OpenAI 인증에 실패했습니다."
            ) from exc

        except RateLimitError as exc:
            raise RuntimeError(
                "OpenAI 요청 한도를 초과했습니다."
            ) from exc

        except APIConnectionError as exc:
            raise RuntimeError(
                "OpenAI 서버에 연결할 수 없습니다."
            ) from exc

        except APIError as exc:
            raise RuntimeError(
                "OpenAI embedding 호출에 실패했습니다."
            ) from exc

        if not response.data:
            raise RuntimeError(
                "OpenAI가 embedding 결과를 반환하지 않았습니다."
            )

        embedding = response.data[0].embedding

        if not embedding:
            raise RuntimeError(
                "OpenAI가 빈 embedding 결과를 반환했습니다."
            )

        return ReportEmbeddingResponse(
            embedding=embedding,
            model=self.settings.openai_embedding_model,
        )