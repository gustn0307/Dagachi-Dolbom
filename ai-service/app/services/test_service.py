from app.schemas.test import EchoRequest, EchoResponse


def create_echo_response(
    request: EchoRequest,
) -> EchoResponse:
    """
    Spring Boot ↔ FastAPI 내부 통신을 확인하기 위한 테스트 로직입니다.

    현재는 AI 처리를 하지 않고 전달받은 message를 그대로 반환합니다.

    이후 실제 AI 기능에서는 이 services 계층에서
    OpenAI API 호출, RAG 검색, 분석 등의 로직을 수행합니다.

    API Router는 HTTP 요청/응답 처리에 집중하고,
    실제 처리 로직은 services 계층으로 분리합니다.
    """

    return EchoResponse(
        message=request.message,
        source="fastapi",
    )