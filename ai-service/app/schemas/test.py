from pydantic import BaseModel


class EchoRequest(BaseModel):
    """
    Spring Boot에서 FastAPI로 전달하는 테스트 요청 DTO입니다.

    FastAPI에서는 Pydantic BaseModel을 이용해
    요청 JSON의 구조와 타입을 검증합니다.

    Spring Boot의 Request DTO와 비슷한 역할입니다.

    요청 예:
    {
        "message": "Spring Boot에서 보낸 메시지"
    }
    """

    message: str


class EchoResponse(BaseModel):
    """
    FastAPI가 Spring Boot로 반환하는 테스트 응답 DTO입니다.

    응답 데이터 구조를 명시해 두면
    FastAPI가 반환값을 검증할 수 있고
    Swagger 문서에도 응답 Schema가 자동으로 표시됩니다.
    """

    message: str
    source: str