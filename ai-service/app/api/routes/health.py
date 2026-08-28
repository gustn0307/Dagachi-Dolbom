from fastapi import APIRouter


# APIRouter는 관련 API endpoint들을 묶어서 관리하기 위한 객체입니다.
#
# Spring Boot의 @RestController와 비슷한 역할로 이해할 수 있습니다.
# 각 기능별 Router를 분리하면 main.py가 비대해지는 것을 방지할 수 있습니다.
router = APIRouter()


# FastAPI 서버가 정상적으로 실행 중인지 확인하기 위한 Health Check API입니다.
#
# 사용 예:
# GET http://localhost:8000/health
#
# Docker, AWS, Spring Boot 또는 배포 시스템에서
# AI 서버의 정상 동작 여부를 빠르게 확인하는 용도로 사용합니다.
@router.get("/health")
def health_check():
    return {
        "status": "ok"
    }