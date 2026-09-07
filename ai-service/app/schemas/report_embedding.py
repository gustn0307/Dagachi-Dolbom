from pydantic import BaseModel, Field


class ReportEmbeddingRequest(BaseModel):
    """
    제보 embedding 생성 요청 Schema입니다.

    Spring Boot에서 Report.content만 전달합니다.
    개인정보, 주소, 좌표, 이미지 등은 embedding 입력에서 제외합니다.
    """

    content: str = Field(
        ...,
        min_length=1,
        max_length=5000,
        description="embedding을 생성할 제보 원문",
    )


class ReportEmbeddingResponse(BaseModel):
    """
    제보 embedding 생성 응답 Schema입니다.
    """

    embedding: list[float] = Field(
        ...,
        min_length=1,
        description="OpenAI가 생성한 embedding 벡터",
    )

    model: str = Field(
        ...,
        description="embedding 생성에 사용된 OpenAI 모델",
    )