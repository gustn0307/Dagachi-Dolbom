from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    AI 서비스에서 사용하는 환경설정을 관리합니다.

    실제 비밀값이나 환경별 설정은 코드에 직접 작성하지 않고
    .env 파일 또는 운영 환경변수에서 읽어옵니다.

    개발 환경:
        ai-service/.env

    운영 환경:
        Docker / AWS 환경변수
    """

    # 애플리케이션 기본 정보
    app_name: str = "Dagachi Dolbom AI Service"
    app_version: str = "0.1.0"

    # OpenAI API Key
    #
    # 아직 OpenAI 기능을 연결하지 않았으므로 기본값을 빈 문자열로 둡니다.
    # 이후 실제 AI 기능 구현 시 .env에서 값을 주입합니다.
    openai_api_key: str = ""

    # .env 파일을 읽도록 설정합니다.
    #
    # extra="ignore":
    # 아직 Settings 클래스에 선언하지 않은 환경변수가 .env에 있어도
    # 애플리케이션 실행을 실패시키지 않습니다.
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    """
    Settings 객체를 애플리케이션 실행 중 한 번 생성한 뒤 재사용합니다.

    매 요청마다 .env 파일을 다시 읽지 않도록 캐시하는 역할입니다.
    """

    return Settings()