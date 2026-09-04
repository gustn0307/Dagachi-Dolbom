from pydantic import BaseModel, Field


class ReportSummaryRequest(BaseModel):
    """
    제보 AI 요약 요청 Schema입니다.

    Spring Boot에서 제보 원문만 전달받습니다.
    개인정보, 위치정보, 이미지 등은 첫 구현 범위에서 제외합니다.
    """

    # 지나치게 짧거나 긴 AI 요청을 방지하기 위한 기본 검증입니다.
    # 최대 길이 5000자는 현재 AI 내부 요청의 초기 제한값입니다.
    content: str = Field(
        ...,
        min_length=1,
        max_length=5000,
        description="AI가 요약할 제보 원문",
    )


class ReportSummaryResponse(BaseModel):
    """
    제보 AI 요약 응답 Schema입니다.
    """

    # 기관 담당자에게 보여줄 AI 요약 결과입니다.
    summary: str = Field(
        ...,
        description="AI가 생성한 제보 요약",
    )

    # 이후 Spring Boot에서 AIAnalysis.modelName에 저장할 수 있도록
    # 실제 사용 모델명을 함께 반환합니다.
    model: str = Field(
        ...,
        description="요약 생성에 사용된 OpenAI 모델",
    )