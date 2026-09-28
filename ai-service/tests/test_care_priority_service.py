import json
import unittest

from app.schemas.care_priority import CarePriorityCandidate, CarePriorityRequest
from app.services.care_priority_service import CarePriorityService


class FakeResponse:
    def __init__(self, output_text: str) -> None:
        self.output_text = output_text


class FakeResponses:
    def __init__(self, output_text: str) -> None:
        self.output_text = output_text

    def create(self, **kwargs):
        return FakeResponse(self.output_text)


class FakeOpenAIClient:
    def __init__(self, output_text: str) -> None:
        self.responses = FakeResponses(output_text)


def request_fixture() -> CarePriorityRequest:
    return CarePriorityRequest(
        candidates=[
            CarePriorityCandidate(
                candidateKey="recipient-1",
                daysSinceLastCheck=21,
                recentActivityCount=0,
                mealConcernCount=2,
                healthConcernCount=1,
                supportNeededCount=1,
                hasUpcomingActivity=False,
            )
        ]
    )


class CarePriorityServiceTest(unittest.TestCase):
    def test_req_ai_09_req_ai_10_analyze_returns_valid_recommendation(self):
        output = json.dumps(
            {
                "recommendations": [
                    {
                        "candidateKey": "recipient-1",
                        "riskLevel": "HIGH",
                        "score": 88,
                        "reasons": ["마지막 안부 확인 후 21일 경과"],
                        "recommendedAction": "빠른 안부 확인",
                    }
                ]
            },
            ensure_ascii=False,
        )

        result = CarePriorityService(FakeOpenAIClient(output)).analyze(request_fixture())

        self.assertEqual(result.recommendations[0].candidateKey, "recipient-1")
        self.assertEqual(result.recommendations[0].riskLevel, "HIGH")
        self.assertEqual(result.recommendations[0].score, 88)

    def test_req_ai_09_req_ai_10_analyze_rejects_unknown_candidate(self):
        output = json.dumps(
            {
                "recommendations": [
                    {
                        "candidateKey": "recipient-999",
                        "riskLevel": "HIGH",
                        "score": 90,
                        "reasons": ["근거"],
                        "recommendedAction": "확인",
                    }
                ]
            },
            ensure_ascii=False,
        )

        with self.assertRaisesRegex(RuntimeError, "대상자가 요청과 일치하지 않습니다"):
            CarePriorityService(FakeOpenAIClient(output)).analyze(request_fixture())


if __name__ == "__main__":
    unittest.main()
