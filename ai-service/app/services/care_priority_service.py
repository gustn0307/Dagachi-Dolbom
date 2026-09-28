import json

from openai import APIConnectionError, APIError, AuthenticationError, OpenAI, RateLimitError

from app.core.config import get_settings
from app.core.openai_client import get_openai_client
from app.schemas.care_priority import CarePriorityRequest, CarePriorityResponse


class CarePriorityService:
    def __init__(self, client: OpenAI | None = None) -> None:
        self.settings = get_settings()
        self.client = client or get_openai_client()

    def analyze(self, request: CarePriorityRequest) -> CarePriorityResponse:
        payload = request.model_dump_json()
        try:
            response = self.client.responses.create(
                model=self.settings.openai_model,
                instructions=(
                    "당신은 지역 돌봄 기관 담당자의 업무 우선순위를 돕는 분석 도우미입니다. "
                    "입력된 수치만 사용하고 의료 진단이나 새로운 사실을 만들지 마세요. "
                    "후보를 0~100점으로 평가하고 HIGH, MEDIUM, LOW 중 하나로 분류하세요. "
                    "오래 확인하지 않았거나 식사·건강·추가 지원 우려가 반복되고 예정 활동이 없으면 우선도를 높이세요. "
                    "모든 후보를 포함하고 candidateKey는 그대로 유지하세요. "
                    "JSON 객체만 반환하세요. 형식은 "
                    '{"recommendations":[{"candidateKey":"...","riskLevel":"HIGH",'
                    '"score":80,"reasons":["근거"],"recommendedAction":"권장 조치"}]} 입니다.'
                ),
                input=payload,
            )
        except AuthenticationError as exc:
            raise RuntimeError("OpenAI 인증에 실패했습니다.") from exc
        except RateLimitError as exc:
            raise RuntimeError("OpenAI 요청 한도를 초과했습니다.") from exc
        except APIConnectionError as exc:
            raise RuntimeError("OpenAI 서버에 연결할 수 없습니다.") from exc
        except APIError as exc:
            raise RuntimeError("OpenAI 호출에 실패했습니다.") from exc

        text = response.output_text.strip()
        if text.startswith("```json"):
            text = text[7:]
        if text.startswith("```"):
            text = text[3:]
        if text.endswith("```"):
            text = text[:-3]

        try:
            parsed = json.loads(text.strip())
            parsed["model"] = self.settings.openai_model
            result = CarePriorityResponse.model_validate(parsed)
        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            raise RuntimeError("OpenAI가 올바르지 않은 우선순위 결과를 반환했습니다.") from exc

        allowed_keys = {candidate.candidateKey for candidate in request.candidates}
        returned_keys = {item.candidateKey for item in result.recommendations}
        if returned_keys != allowed_keys:
            raise RuntimeError("OpenAI 우선순위 결과의 대상자가 요청과 일치하지 않습니다.")
        if any(item.riskLevel not in {"HIGH", "MEDIUM", "LOW"} for item in result.recommendations):
            raise RuntimeError("OpenAI가 지원하지 않는 위험도 값을 반환했습니다.")

        result.recommendations.sort(key=lambda item: item.score, reverse=True)
        return result
