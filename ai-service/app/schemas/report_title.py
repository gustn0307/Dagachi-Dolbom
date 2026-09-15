from pydantic import BaseModel, Field


class ReportTitleRequest(BaseModel):
    """
    제보 AI 제목 생성 요청 Schema입니다.

    목록 화면 전용 한줄 제목을 만들기 위한 요청이며,
    REPORT_SUMMARY(상세 화면 문단 요약)와는 완전히 분리된 별도 분석입니다.
    """

    content: str = Field(
        ...,
        min_length=1,
        max_length=5000,
        description="AI가 제목을 생성할 제보 원문",
    )


class ReportTitleResponse(BaseModel):
    title: str = Field(
        ...,
        description="AI가 생성한 제보 목록용 한줄 제목",
    )

    model: str = Field(
        ...,
        description="제목 생성에 사용된 OpenAI 모델",
    )