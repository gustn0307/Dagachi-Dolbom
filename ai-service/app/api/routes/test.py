from fastapi import APIRouter

from app.schemas.test import EchoRequest, EchoResponse
from app.services.test_service import create_echo_response


# AI 서비스의 내부 통신 테스트용 Router입니다.
#
# prefix를 사용하면 이 Router에 정의된 모든 API 앞에
# /api/v1/test가 자동으로 붙습니다.
#
# 예:
# @router.post("/echo")
# 실제 URL → POST /api/v1/test/echo
router = APIRouter(
    prefix="/api/v1/test",
    tags=["test"],
)


@router.post(
    "/echo",
    response_model=EchoResponse,
)
def echo(
    request: EchoRequest,
) -> EchoResponse:
    """
    Spring Boot와 FastAPI 사이의 HTTP 통신을 확인하기 위한 API입니다.

    전달받은 message를 그대로 반환하며,
    실제 AI 기능은 아직 수행하지 않습니다.
    """

    return create_echo_response(request)