import json

from openai import (
    APIConnectionError,
    APIError,
    AuthenticationError,
    OpenAI,
    RateLimitError,
)

from app.core.config import get_settings
from app.core.openai_client import get_openai_client
from app.schemas.activity_matching import (
    ActivityMatchingRequest,
    ActivityMatchingResponse,
)


class ActivityMatchingService:

    def __init__(self, client: OpenAI | None = None) -> None:
        self.settings = get_settings()
        self.client = client or get_openai_client()

    def match(
        self,
        request: ActivityMatchingRequest,
    ) -> ActivityMatchingResponse:

        payload = request.model_dump_json()

        response = self._call_openai(payload)

        return self._parse_response(
            request,
            response.output_text,
        )

    # OpenAI 호출이 일시적으로 실패하면 1회만 다시 시도합니다.
    def _call_openai(self, payload: str):

        last_error = None

        for attempt in range(2):
            try:
                return self.client.responses.create(
                    model=self.settings.openai_model,
                    instructions=(
                        "당신은 지역 돌봄 활동과 안부파트너를 연결하는 추천 도우미입니다. "
                        "입력된 정보만 사용해서 모든 후보 활동의 추천 순위를 정하세요. "

                        "다음 요소를 함께 고려하세요. "
                        "1. 이용자와 활동 장소의 거리 "
                        "2. 대상자의 마지막 안부 확인 이후 경과 기간 "
                        "3. 이용자가 활동한 적 있는 지역 "
                        "4. 이용자가 경험한 대상자 연령대 "
                        "5. 활동의 필요 인원과 현재 승인 인원 "
                        "6. 대상자의 최근 승인된 활동 기록 "

                        "최근 활동 기록이 없다는 사실 자체를 위험 신호로 판단하지 마세요. "
                        "체크리스트에 기록된 사실을 넘어 의료 진단이나 새로운 사실을 만들지 마세요. "
                        "특정 건강 상태를 추측하지 마세요. "

                        "모든 후보를 반드시 한 번씩 포함하세요. "
                        "rank는 1부터 후보 수까지 중복 없이 부여하세요. "
                        "1위가 가장 추천되는 활동입니다. "
                        "추천 이유는 사용자가 이해할 수 있는 짧은 한국어 문장으로 작성하세요. "
                        "점수는 만들지 마세요. "

                        "JSON 객체만 반환하세요. "
                        "형식은 "
                        '{"recommendations":['
                        '{"activityId":1,"rank":1,"reason":"추천 이유"}'
                        "]}"
                        " 입니다."
                    ),
                    input=payload,
                )

            except AuthenticationError as exc:
                # 인증 오류는 다시 호출해도 해결되지 않으므로 즉시 실패합니다.
                raise RuntimeError(
                    "OpenAI 인증에 실패했습니다."
                ) from exc

            except (RateLimitError, APIConnectionError, APIError) as exc:
                last_error = exc

                # 첫 번째 실패일 때만 한 번 더 시도합니다.
                if attempt == 0:
                    continue

        raise RuntimeError(
            "OpenAI 활동 매칭 호출에 실패했습니다."
        ) from last_error

    # AI 응답 형식과 후보 ID / 순위가 올바른지 검증합니다.
    def _parse_response(
        self,
        request: ActivityMatchingRequest,
        output_text: str,
    ) -> ActivityMatchingResponse:

        text = output_text.strip()

        if text.startswith("```json"):
            text = text[7:]

        if text.startswith("```"):
            text = text[3:]

        if text.endswith("```"):
            text = text[:-3]

        try:
            parsed = json.loads(text.strip())

            parsed["model"] = self.settings.openai_model

            result = ActivityMatchingResponse.model_validate(parsed)

        except (json.JSONDecodeError, TypeError, ValueError) as exc:
            raise RuntimeError(
                "OpenAI가 올바르지 않은 활동 매칭 결과를 반환했습니다."
            ) from exc

        requested_ids = {
            candidate.activityId
            for candidate in request.candidates
        }

        returned_ids = {
            recommendation.activityId
            for recommendation in result.recommendations
        }

        # 요청 후보와 AI가 반환한 후보가 정확히 같아야 합니다.
        if returned_ids != requested_ids:
            raise RuntimeError(
                "OpenAI 활동 매칭 결과의 후보가 요청과 일치하지 않습니다."
            )

        # 중복 activityId도 허용하지 않습니다.
        if len(result.recommendations) != len(request.candidates):
            raise RuntimeError(
                "OpenAI 활동 매칭 결과에 중복 후보가 있습니다."
            )

        ranks = sorted(
            recommendation.rank
            for recommendation in result.recommendations
        )

        expected_ranks = list(
            range(1, len(request.candidates) + 1)
        )

        # 순위는 반드시 1, 2, 3 ... 후보 수까지 정확히 한 번씩 있어야 합니다.
        if ranks != expected_ranks:
            raise RuntimeError(
                "OpenAI 활동 매칭 결과의 순위가 올바르지 않습니다."
            )

        result.recommendations.sort(
            key=lambda recommendation: recommendation.rank
        )

        return result