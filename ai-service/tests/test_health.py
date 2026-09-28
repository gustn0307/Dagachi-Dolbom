from fastapi.testclient import TestClient

from app.main import app


# FastAPI 애플리케이션을 실제 uvicorn 서버로 실행하지 않고,
# 메모리 안에서 HTTP 요청처럼 테스트하기 위한 TestClient입니다.
#
# 실제 네트워크 포트(localhost:8000)를 사용하지 않기 때문에
# 빠르고 CI 환경에서도 안정적으로 실행할 수 있습니다.
client = TestClient(app)


def test_health_정상응답을_반환한다():
    """
    GET /health 호출 시
    HTTP 200과 {"status": "ok"} 응답이 반환되는지 확인합니다.
    """

    # when
    response = client.get("/health")

    # then
    assert response.status_code == 200
    assert response.json() == {
        "status": "ok"
    }