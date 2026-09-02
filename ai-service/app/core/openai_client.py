from functools import lru_cache

from openai import OpenAI

from app.core.config import get_settings


@lru_cache
def get_openai_client() -> OpenAI:
    """
    OpenAI API Client를 한 번 생성한 뒤 재사용합니다.

    API Key는 코드에 직접 작성하지 않고
    Settings를 통해 환경변수에서 읽습니다.
    """

    settings = get_settings()

    # AI 기능을 실제 호출하는 시점에 API Key 존재 여부를 확인합니다.
    # Key가 없어도 FastAPI 자체와 /health는 실행될 수 있도록
    # 애플리케이션 시작 단계에서는 검사하지 않습니다.
    if not settings.openai_api_key:
        raise RuntimeError(
            "OPENAI_API_KEY가 설정되지 않았습니다."
        )

    return OpenAI(
        api_key=settings.openai_api_key,
    )