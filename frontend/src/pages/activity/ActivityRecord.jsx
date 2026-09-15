import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { userApi } from "../../api/userApi";

const OPTION_LABELS = {
  YES: "예",
  NO: "아니오",
  UNKNOWN: "확인 어려움",
};

const REVIEW_STATUS_LABELS = {
  DRAFT: "작성 중",
  SUBMITTED: "제출 완료",
  APPROVED: "승인 완료",
  NEEDS_REVISION: "보완 요청",
  REJECTED: "반려",
};

/*
 * 서버의 LocalDateTime 값을
 * datetime-local input에서 사용할 형식으로 변환합니다.
 */
function toInputDateTime(value) {
  if (!value) {
    return "";
  }

  return value.slice(0, 16);
}

/*
 * datetime-local input 값을
 * Backend LocalDateTime 요청값으로 변환합니다.
 */
function toApiDateTime(value) {
  if (!value) {
    return null;
  }

  return value.length === 16 ? `${value}:00` : value;
}

/*
 * 날짜/시간을 화면 표시용 한국어 형식으로 변환합니다.
 */
function formatDateTime(value) {
  if (!value) {
    return "-";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function ActivityRecord() {
  const navigate = useNavigate();
  const { recordId } = useParams();

  /*
   * RECORD-02에서 받은 ActivityRecord 전체 상태입니다.
   */
  const [record, setRecord] = useState(null);

  /*
   * CHECK-01에서 받은 체크리스트 문항입니다.
   * selectedValue도 각 문항 안에서 함께 관리합니다.
   */
  const [checklistItems, setChecklistItems] = useState([]);

  /*
   * RECORD-03에서 저장할 ActivityRecord 기본 입력값입니다.
   */
  const [form, setForm] = useState({
    visitResult: "",
    completedAt: "",
    specialNote: "",
  });

  /*
   * 대상자가 화면에 직접 서명할 Canvas입니다.
   */
  const signatureCanvasRef = useRef(null);

  /*
   * 현재 Pointer가 눌린 상태에서
   * 서명선을 그리고 있는지 보관합니다.
   */
  const signatureDrawingRef = useRef(false);

  /*
   * 현재 서명에 사용 중인 Pointer의 ID입니다.
   * 두 손가락이 동시에 닿더라도 하나의 Pointer만 서명에 사용합니다.
   */
  const signaturePointerIdRef = useRef(null);

  /*
   * 선을 이어 그리기 위해
   * 직전에 지나간 좌표를 보관합니다.
   */
  const signatureLastPointRef = useRef(null);

  /*
   * 서명을 구성하는 원본 좌표 데이터입니다.
   * Canvas 크기가 바뀌어도 이 좌표를 기준으로 다시 그립니다.
   */
  const signatureStrokesRef = useRef([]);

  /*
   * 현재 그리고 있는 한 획(stroke)을 가리킵니다.
   */
  const signatureCurrentStrokeRef = useRef(null);

  /*
   * 서명을 처음 작성한 Canvas의 기준 크기입니다.
   * 이후 리사이즈 시 이 크기를 기준으로 비율을 계산합니다.
   */
  const signatureBaseSizeRef = useRef(null);

  const getSignatureTransform = (canvas) => {
    const baseSize = signatureBaseSizeRef.current;

    if (!canvas || !baseSize) {
      return null;
    }

    const rect = canvas.getBoundingClientRect();

    if (rect.width === 0 || rect.height === 0) {
      return null;
    }

    /*
     * 가로/세로 중 더 작은 비율을 사용해서
     * 서명의 원래 비율을 유지합니다.
     */
    const scale = Math.min(
      rect.width / baseSize.width,
      rect.height / baseSize.height,
    );

    const drawWidth = baseSize.width * scale;
    const drawHeight = baseSize.height * scale;

    return {
      scale,
      offsetX: (rect.width - drawWidth) / 2,
      offsetY: (rect.height - drawHeight) / 2,
    };
  };

  /*
   * ResizeObserver 같은 비동기 Canvas 로직에서도
   * 현재 서명 존재 여부를 항상 최신 값으로 확인하기 위한 Ref입니다.
   */
  const hasSignatureDrawingRef = useRef(false);

  /*
   * 실제로 서명선이 하나라도 그려졌는지 관리합니다.
   * 빈 Canvas 업로드와 최종 제출 여부 판단에 사용합니다.
   */
  const [hasSignatureDrawing, setHasSignatureDrawing] = useState(false);

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [actionError, setActionError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  /*
   * 성공/오류 메시지를 사용자가 실행한 기능 가까이에 표시하기 위해
   * 메시지가 어느 영역에서 발생했는지 구분합니다.
   * "signature" = 서명 영역
   * "actions" = 임시저장 / 최종 제출 영역
   */
  const [messageTarget, setMessageTarget] = useState("");

  const [saving, setSaving] = useState(false);
  const [uploadingSignature, setUploadingSignature] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  /*
   * DRAFT와 NEEDS_REVISION 상태에서만
   * 활동기록을 수정할 수 있습니다.
   */
  const editable =
    record?.reviewStatus === "DRAFT" ||
    record?.reviewStatus === "NEEDS_REVISION";

  /*
   * CHECK-01과 RECORD-02를 동시에 조회합니다.
   *
   * CHECK-01:
   * - 체크리스트 문항
   * - 선택지
   * - 기존 체크리스트 응답
   *
   * RECORD-02:
   * - 방문 결과
   * - 활동 시작/완료 시각
   * - 특이사항
   * - 서명 여부
   * - 검토 상태
   */
  useEffect(() => {
    let ignore = false;

    const loadActivityRecord = async () => {
      setLoading(true);
      setLoadError("");

      try {
        const [checklistResponse, recordResponse] = await Promise.all([
          userApi.getActivityChecklist(recordId),
          userApi.getActivityRecord(recordId),
        ]);

        if (ignore) {
          return;
        }

        setRecord(recordResponse);

        setChecklistItems(
          Array.isArray(checklistResponse?.items)
            ? checklistResponse.items
            : [],
        );

        setForm({
          visitResult: recordResponse?.visitResult ?? "",

          completedAt: toInputDateTime(recordResponse?.completedAt),

          specialNote: recordResponse?.specialNote ?? "",
        });
      } catch (error) {
        if (ignore) {
          return;
        }

        setLoadError(
          error?.response?.data?.message ?? "활동기록을 불러오지 못했습니다.",
        );
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    };

    loadActivityRecord();

    return () => {
      ignore = true;
    };
  }, [recordId]);

  /*
   * 방문 결과를 변경합니다.
   *
   * NOT_MET이면 체크리스트를 사용하지 않으므로
   * 현재 화면의 체크리스트 답변도 모두 비웁니다.
   */
  const handleVisitResultChange = (event) => {
    const value = event.target.value;

    setActionError("");
    setSuccessMessage("");

    setForm((current) => ({
      ...current,
      visitResult: value,
    }));

    if (value === "NOT_MET") {
      setChecklistItems((items) =>
        items.map((item) => ({
          ...item,
          selectedValue: null,
        })),
      );

      /*
       * 아직 서버에 등록하지 않은 서명은
       * NOT_MET으로 변경할 때 함께 초기화합니다.
       */
      clearSignatureCanvas();
    }
  };

  /*
   * 완료 시각과 특이사항 입력값을 변경합니다.
   */
  const handleFormChange = (event) => {
    const { name, value } = event.target;

    setActionError("");
    setSuccessMessage("");

    setForm((current) => ({
      ...current,
      [name]: value,
    }));
  };

  /*
   * CHECK-01 문항의 선택값을 변경합니다.
   */
  const handleChecklistChange = (itemId, selectedValue) => {
    setActionError("");
    setSuccessMessage("");

    setChecklistItems((items) =>
      items.map((item) =>
        item.id === itemId
          ? {
              ...item,
              selectedValue,
            }
          : item,
      ),
    );
  };

  /*
   * 서명 Canvas에서 Pointer가 눌렸을 때
   * 서명 시작 위치를 기억합니다.
   *
   * 마우스, 손가락, 터치펜 모두 Pointer Event로 동일하게 처리합니다.
   */
  const handleSignaturePointerDown = (event) => {
    const canvas = signatureCanvasRef.current;

    if (!canvas) {
      return;
    }

    /*
     * 이미 다른 Pointer로 서명 중이라면
     * 두 번째 손가락 입력은 무시합니다.
     */
    if (signaturePointerIdRef.current !== null) {
      return;
    }

    /*
     * 마우스는 왼쪽 버튼만 서명 입력으로 사용합니다.
     * 오른쪽 클릭이나 휠 클릭은 무시합니다.
     */
    if (event.pointerType === "mouse" && event.button !== 0) {
      return;
    }

    /*
     * 지금 눌린 Pointer를
     * 현재 서명에 사용하는 Pointer로 기억합니다.
     */
    signaturePointerIdRef.current = event.pointerId;

    /*
     * Canvas가 화면에서 실제로 표시되는 위치와 크기를 가져옵니다.
     */
    const rect = canvas.getBoundingClientRect();

    /*
     * 첫 서명을 시작한 순간의 Canvas 크기를
     * 이후 리사이즈 계산의 기준 크기로 사용합니다.
     */
    if (!signatureBaseSizeRef.current) {
      signatureBaseSizeRef.current = {
        width: rect.width,
        height: rect.height,
      };
    }

    const transform = getSignatureTransform(canvas);

    if (!transform) {
      return;
    }

    /*
     * 현재 화면에서 Pointer가 위치한 좌표입니다.
     * 실제 Canvas에 즉시 선을 그릴 때 사용합니다.
     */
    const point = {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top,
    };

    /*
     * 현재 화면 좌표를 원본 서명 좌표로 되돌립니다.
     * 이 좌표가 리사이즈와 상관없는 진짜 서명 데이터가 됩니다.
     */
    const basePoint = {
      x: (point.x - transform.offsetX) / transform.scale,
      y: (point.y - transform.offsetY) / transform.scale,
    };

    /*
     * 새 획의 시작점입니다.
     * 아직 움직이지 않았으므로 strokes에는 넣지 않습니다.
     */
    signatureCurrentStrokeRef.current = [basePoint];

    signatureDrawingRef.current = true;
    signatureLastPointRef.current = point;

    /*
     * Pointer가 Canvas 밖으로 조금 벗어나도
     * 계속 같은 서명 동작으로 추적할 수 있게 합니다.
     */
    canvas.setPointerCapture(event.pointerId);
  };

  /*
   * Pointer가 서명 Canvas 위에서 움직일 때
   * 직전 위치부터 현재 위치까지 선을 이어 그립니다.
   */
  const handleSignaturePointerMove = (event) => {
    const canvas = signatureCanvasRef.current;
    const lastPoint = signatureLastPointRef.current;
    const currentStroke = signatureCurrentStrokeRef.current;

    if (
      !canvas ||
      !signatureDrawingRef.current ||
      !lastPoint ||
      !currentStroke ||
      signaturePointerIdRef.current !== event.pointerId
    ) {
      return;
    }

    const rect = canvas.getBoundingClientRect();

    /*
     * 현재 Pointer 위치를 Canvas 내부 좌표로 변환합니다.
     */
    const currentPoint = {
      x: event.clientX - rect.left,
      y: event.clientY - rect.top,
    };

    const transform = getSignatureTransform(canvas);

    if (!transform) {
      return;
    }

    /*
     * 현재 화면 좌표를 원본 서명 좌표로 변환해서
     * 현재 획에 계속 저장합니다.
     */
    const basePoint = {
      x: (currentPoint.x - transform.offsetX) / transform.scale,
      y: (currentPoint.y - transform.offsetY) / transform.scale,
    };

    currentStroke.push(basePoint);

    /*
     * PointerDown의 한 점만 있을 때는 서명으로 인정하지 않고,
     * 실제 첫 이동이 발생해서 두 번째 점이 생긴 순간에만
     * 이 획을 전체 서명 데이터에 등록합니다.
     */
    if (currentStroke.length === 2) {
      signatureStrokesRef.current.push(currentStroke);
    }

    const context = canvas.getContext("2d");

    if (!context) {
      return;
    }

    /*
     * 서명선 모양입니다.
     * - 어두운 선
     * - 3px 굵기
     * - 선 끝과 연결부를 둥글게 처리
     */
    context.strokeStyle = "#2f2f2f";
    context.lineWidth = 3;
    context.lineCap = "round";
    context.lineJoin = "round";

    context.beginPath();
    context.moveTo(lastPoint.x, lastPoint.y);
    context.lineTo(currentPoint.x, currentPoint.y);
    context.stroke();

    hasSignatureDrawingRef.current = true;

    /*
     * 실제 이동이 발생해 선을 그렸으므로
     * 이제 Canvas에는 등록할 서명이 있다고 판단합니다.
     */
    setHasSignatureDrawing(true);

    /*
     * 다음 선의 시작점은 방금 도착한 위치가 됩니다.
     */
    signatureLastPointRef.current = currentPoint;
  };

  /*
   * 현재 서명에 사용 중인 Pointer를 떼거나
   * 해당 Pointer 동작이 취소되면 선 그리기를 종료합니다.
   */
  const handleSignaturePointerEnd = (event) => {
    const canvas = signatureCanvasRef.current;

    /*
     * 현재 서명에 사용 중인 Pointer가 아니라면
     * 종료 이벤트도 무시합니다.
     */
    if (signaturePointerIdRef.current !== event.pointerId) {
      return;
    }

    signatureDrawingRef.current = false;
    signatureLastPointRef.current = null;

    /*
     * 현재 사용 중이던 Pointer도 해제합니다.
     */
    signaturePointerIdRef.current = null;

    if (canvas?.hasPointerCapture(event.pointerId)) {
      canvas.releasePointerCapture(event.pointerId);
    }
  };

  /*
   * Canvas에 그려진 서명을 PNG File 객체로 변환합니다.
   *
   * 기존 RECORD-04는 MultipartFile을 받으므로
   * 브라우저에서도 일반 이미지 파일과 같은 File 형태로 만들어 전달합니다.
   */
  const createSignatureFile = () => {
    const canvas = signatureCanvasRef.current;

    if (!canvas) {
      return Promise.resolve(null);
    }

    return new Promise((resolve) => {
      canvas.toBlob((blob) => {
        if (!blob) {
          resolve(null);
          return;
        }

        const file = new File([blob], `signature-${recordId}.png`, {
          type: "image/png",
        });

        resolve(file);
      }, "image/png");
    });
  };

  /*
   * 서명 Canvas를 비우고
   * 현재 작성 중인 서명 상태도 함께 초기화합니다.
   */
  const clearSignatureCanvas = () => {
    const canvas = signatureCanvasRef.current;

    if (!canvas) {
      return;
    }

    const context = canvas.getContext("2d");

    if (!context) {
      return;
    }

    /*
     * 이후 고해상도 화면 대응을 위해 Canvas에 배율이 적용되더라도
     * 실제 Canvas 전체 픽셀 영역을 확실하게 지웁니다.
     */
    context.save();
    context.setTransform(1, 0, 0, 1, 0, 0);
    context.clearRect(0, 0, canvas.width, canvas.height);
    context.restore();

    signatureDrawingRef.current = false;
    signaturePointerIdRef.current = null;
    signatureLastPointRef.current = null;

    signatureCurrentStrokeRef.current = null;
    signatureStrokesRef.current = [];
    signatureBaseSizeRef.current = null;

    signatureCurrentStrokeRef.current = null;
    hasSignatureDrawingRef.current = false;
    setHasSignatureDrawing(false);
  };

  /*
   * 화면에 표시되는 Canvas 크기가 변경되더라도
   * 기존 서명 비율을 유지하면서 보존하고,
   * 고해상도 화면에 맞춰 실제 픽셀 크기를 다시 설정합니다.
   */
  const initializeSignatureCanvas = () => {
    const canvas = signatureCanvasRef.current;

    if (!canvas) {
      return;
    }

    const rect = canvas.getBoundingClientRect();

    if (rect.width === 0 || rect.height === 0) {
      return;
    }

    const dpr = window.devicePixelRatio || 1;

    /*
     * Canvas의 실제 해상도를 현재 화면 크기에 맞춥니다.
     */
    canvas.width = Math.round(rect.width * dpr);
    canvas.height = Math.round(rect.height * dpr);

    const context = canvas.getContext("2d");

    if (!context) {
      return;
    }

    /*
     * 이후 좌표는 CSS 픽셀 기준으로 그릴 수 있도록
     * DPR 배율을 적용합니다.
     */
    context.setTransform(dpr, 0, 0, dpr, 0, 0);

    const transform = getSignatureTransform(canvas);

    if (!transform) {
      return;
    }

    /*
     * 저장해둔 원본 좌표를 기준으로
     * 현재 Canvas 크기에 맞게 서명을 처음부터 다시 그립니다.
     */
    signatureStrokesRef.current.forEach((stroke) => {
      if (stroke.length < 2) {
        return;
      }

      context.strokeStyle = "#2f2f2f";
      context.lineWidth = 3;
      context.lineCap = "round";
      context.lineJoin = "round";

      context.beginPath();

      const firstPoint = stroke[0];

      context.moveTo(
        firstPoint.x * transform.scale + transform.offsetX,
        firstPoint.y * transform.scale + transform.offsetY,
      );

      for (let index = 1; index < stroke.length; index += 1) {
        const point = stroke[index];

        context.lineTo(
          point.x * transform.scale + transform.offsetX,
          point.y * transform.scale + transform.offsetY,
        );
      }

      context.stroke();
    });
  };

  /*
   * 서명 Canvas가 화면에 나타나면 최초 크기를 설정하고,
   * 이후 실제 표시 크기가 변경될 때마다 서명을 보존한 채 다시 맞춥니다.
   */
  useEffect(() => {
    /*
     * 서명판은 MET + 수정 가능한 상태에서만 화면에 존재합니다.
     */
    if (form.visitResult !== "MET" || !editable) {
      return;
    }

    const canvas = signatureCanvasRef.current;

    if (!canvas) {
      return;
    }

    /*
     * Canvas가 처음 나타났을 때
     * 현재 화면 크기와 DPR에 맞춰 최초 초기화합니다.
     */
    initializeSignatureCanvas();

    let resizeTimer = null;

    const resizeObserver = new ResizeObserver(() => {
      /*
       * 연속해서 크기가 변경되는 동안에는
       * 이전 예약을 취소하고 다시 150ms를 기다립니다.
       */
      if (resizeTimer) {
        window.clearTimeout(resizeTimer);
      }

      resizeTimer = window.setTimeout(() => {
        /*
         * 리사이즈 중이던 Pointer 상태를 종료합니다.
         * 화면 크기가 바뀌는 순간 이전 좌표와 새 좌표가
         * 잘못 이어지는 것을 방지합니다.
         */
        signatureDrawingRef.current = false;
        signaturePointerIdRef.current = null;
        signatureLastPointRef.current = null;
        signatureCurrentStrokeRef.current = null;

        initializeSignatureCanvas();
      }, 150);
    });

    resizeObserver.observe(canvas);

    /*
     * 서명판이 사라지거나 페이지가 변경되면
     * Observer와 남아 있는 타이머를 정리합니다.
     */
    return () => {
      resizeObserver.disconnect();

      if (resizeTimer) {
        window.clearTimeout(resizeTimer);
      }
    };
  }, [form.visitResult, editable]);

  /*
   * 현재 화면 상태를 RECORD-03 요청 DTO 구조로 변환합니다.
   *
   * MET:
   * 답변한 문항만 responses에 포함합니다.
   *
   * NOT_MET:
   * Backend 정책에 따라 responses는 빈 배열입니다.
   */
  const createDraftRequest = () => {
    const responses =
      form.visitResult === "MET"
        ? checklistItems
            .filter(
              (item) => item.selectedValue != null && item.selectedValue !== "",
            )
            .map((item) => ({
              itemId: item.id,
              selectedValue: item.selectedValue,
              textValue: null,
            }))
        : [];

    return {
      visitResult: form.visitResult || null,

      completedAt: toApiDateTime(form.completedAt),

      specialNote: form.specialNote,

      responses,
    };
  };

  /*
   * RECORD-03 공동 Draft를 저장합니다.
   */
  const handleSaveDraft = async () => {
    setMessageTarget("actions");

    setSaving(true);
    setActionError("");
    setSuccessMessage("");

    try {
      const response = await userApi.saveActivityRecordDraft(
        recordId,
        createDraftRequest(),
      );

      setRecord(response);

      /*
       * Backend에서 공백 특이사항 등을 정리했을 수 있으므로
       * 저장된 최신 값을 화면에도 반영합니다.
       */
      setForm((current) => ({
        ...current,

        visitResult: response?.visitResult ?? "",

        completedAt: toInputDateTime(response?.completedAt),

        specialNote: response?.specialNote ?? "",
      }));

      setSuccessMessage("임시저장되었습니다.");
    } catch (error) {
      setActionError(
        error?.response?.data?.message ?? "활동기록 저장에 실패했습니다.",
      );
    } finally {
      setSaving(false);
    }
  };

  /*
   * RECORD-04 대상자 서명을 업로드합니다.
   * Canvas에 작성한 대상자 서명을 등록합니다.
   *
   * 1. 현재 화면의 Draft를 먼저 저장해 MET 상태를 DB에 반영하고
   * 2. Canvas를 PNG File로 변환한 뒤
   * 3. 기존 RECORD-04 서명 업로드 API를 호출합니다.
   */
  const handleSignatureUpload = async () => {
    setMessageTarget("signature");

    /*
     * 실제 선을 그리지 않은 빈 Canvas는 등록하지 않습니다.
     */
    if (!hasSignatureDrawingRef.current) {
      setActionError("서명을 먼저 작성해주세요.");
      return;
    }

    if (form.visitResult !== "MET") {
      setActionError("대상자를 만남으로 선택해주세요.");
      return;
    }

    setUploadingSignature(true);
    setActionError("");
    setSuccessMessage("");

    try {
      /*
       * RECORD-04는 DB에 저장된 visitResult가 MET이어야 하므로
       * 현재 화면 내용을 먼저 RECORD-03으로 저장합니다.
       */
      const savedRecord = await userApi.saveActivityRecordDraft(
        recordId,
        createDraftRequest(),
      );

      setRecord(savedRecord);

      /*
       * Backend에서 공백 특이사항 등을 정리했을 수 있으므로
       * 저장된 최신 Draft 값을 화면에도 반영합니다.
       */
      setForm((current) => ({
        ...current,
        visitResult: savedRecord?.visitResult ?? "",
        completedAt: toInputDateTime(savedRecord?.completedAt),
        specialNote: savedRecord?.specialNote ?? "",
      }));

      /*
       * Canvas에 그려진 서명을 PNG File 객체로 변환합니다.
       */
      const signatureFile = await createSignatureFile();

      if (!signatureFile) {
        throw new Error("SIGNATURE_FILE_CREATION_FAILED");
      }

      /*
       * 기존 RECORD-04 API를 그대로 사용합니다.
       */
      const response = await userApi.uploadActivityRecordSignature(
        recordId,
        signatureFile,
      );

      setRecord((current) => ({
        ...current,
        signatureUploaded: response?.signatureUploaded ?? true,
        signedAt: response?.signedAt ?? current?.signedAt,
      }));

      /*
       * S3 업로드까지 성공한 경우에만
       * 작성 중이던 Canvas를 초기화합니다.
       */
      clearSignatureCanvas();

      setSuccessMessage(
        record?.signatureUploaded
          ? "서명이 교체되었습니다."
          : "서명이 등록되었습니다.",
      );
    } catch (error) {
      setActionError(
        error?.response?.data?.message ??
          (error?.message === "SIGNATURE_FILE_CREATION_FAILED"
            ? "서명 이미지를 생성하지 못했습니다."
            : "서명 등록에 실패했습니다."),
      );
    } finally {
      setUploadingSignature(false);
    }
  };

  /*
   * RECORD-05 최종 제출입니다.
   *
   * RECORD-05는 Request Body를 받지 않고
   * DB에 저장되어 있는 Draft를 검증하기 때문에,
   * 먼저 현재 화면 내용을 RECORD-03으로 저장한 뒤 제출합니다.
   */
  const handleSubmit = async () => {
    /*
     * Canvas에 새로 작성했지만 아직 등록하지 않은 서명이 있으면
     * 기존 서명으로 잘못 제출되는 것을 막습니다.
     */
    if (hasSignatureDrawingRef.current) {
      setMessageTarget("actions");
      setActionError("작성한 서명을 먼저 등록해주세요.");
      setSuccessMessage("");
      return;
    }

    const confirmed = window.confirm("활동기록을 최종 제출하시겠습니까?");

    if (!confirmed) {
      return;
    }

    setMessageTarget("actions");

    setSubmitting(true);
    setActionError("");
    setSuccessMessage("");

    try {
      /*
       * 사용자가 마지막으로 수정한 화면 값을
       * 먼저 공동 Draft에 저장합니다.
       */
      const savedRecord = await userApi.saveActivityRecordDraft(
        recordId,
        createDraftRequest(),
      );

      setRecord(savedRecord);

      /*
       * 저장된 Draft를 RECORD-05로 최종 제출합니다.
       */
      const submittedRecord = await userApi.submitActivityRecord(recordId);

      setRecord(submittedRecord);

      setSuccessMessage("활동기록이 제출되었습니다.");
    } catch (error) {
      setActionError(
        error?.response?.data?.message ?? "활동기록 제출에 실패했습니다.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <main className="activity-record-page">
        <div className="activity-record-state">
          활동기록을 불러오고 있습니다.
        </div>
      </main>
    );
  }

  if (loadError || !record) {
    return (
      <main className="activity-record-page">
        <div className="activity-record-state error">
          {loadError || "활동기록을 찾을 수 없습니다."}
        </div>

        <button type="button" onClick={() => navigate("/volunteer")}>
          봉사 활동으로 돌아가기
        </button>
      </main>
    );
  }

  return (
    <main className="activity-record-page">
      <button
        type="button"
        className="activity-record-back"
        onClick={() => navigate("/volunteer")}
      >
        ← 봉사 활동으로
      </button>

      <header className="activity-record-header">
        <p>ACTIVITY RECORD</p>

        <h1>활동기록 작성</h1>

        <span>
          활동 #{record.activityId}
          {" · "}
          기록 #{record.recordId}
        </span>
      </header>

      <section className="activity-record-summary">
        <article>
          <span>기록 상태</span>

          <strong>
            {REVIEW_STATUS_LABELS[record.reviewStatus] ?? record.reviewStatus}
          </strong>
        </article>

        <article>
          <span>활동 시작</span>

          <strong>{formatDateTime(record.startedAt)}</strong>
        </article>

        <article>
          <span>서명</span>

          <strong>{record.signatureUploaded ? "등록 완료" : "미등록"}</strong>
        </article>
      </section>

      {record.reviewStatus === "NEEDS_REVISION" && record.reviewNote && (
        <section className="activity-record-review-note">
          <strong>기관 보완 요청</strong>

          <p>{record.reviewNote}</p>
        </section>
      )}

      <section className="activity-record-section">
        <div className="activity-record-section-title">
          <span>1</span>

          <div>
            <h2>방문 결과</h2>

            <p>돌봄 대상자를 직접 만났는지 선택해주세요.</p>
          </div>
        </div>

        <div className="activity-record-visit-options">
          <label>
            <input
              type="radio"
              name="visitResult"
              value="MET"
              checked={form.visitResult === "MET"}
              disabled={!editable}
              onChange={handleVisitResultChange}
            />

            <strong>만남</strong>
            <span>대상자를 만나 안부를 확인했습니다.</span>
          </label>

          <label>
            <input
              type="radio"
              name="visitResult"
              value="NOT_MET"
              checked={form.visitResult === "NOT_MET"}
              disabled={!editable || record.signatureUploaded}
              onChange={handleVisitResultChange}
            />

            <strong>미만남</strong>
            <span>방문했지만 대상자를 만나지 못했습니다.</span>
          </label>
        </div>

        {record.signatureUploaded && form.visitResult === "MET" && editable && (
          <p className="activity-record-help">
            서명이 등록된 기록은 미만남으로 변경할 수 없습니다.
          </p>
        )}
      </section>

      {form.visitResult === "MET" && (
        <section className="activity-record-section">
          <div className="activity-record-section-title">
            <span>2</span>

            <div>
              <h2>안부 체크리스트</h2>

              <p>대상자의 현재 상태를 확인해주세요.</p>
            </div>
          </div>

          <div className="activity-record-checklist">
            {checklistItems.map((item, index) => (
              <article key={item.id}>
                <div className="activity-record-question">
                  <span>{index + 1}</span>

                  <strong>{item.question}</strong>

                  {item.required && <em>필수</em>}
                </div>

                <div className="activity-record-answer-options">
                  {(item.options ?? []).map((option) => (
                    <label key={option}>
                      <input
                        type="radio"
                        name={`checklist-${item.id}`}
                        value={option}
                        checked={item.selectedValue === option}
                        disabled={!editable}
                        onChange={() => handleChecklistChange(item.id, option)}
                      />

                      <span>{OPTION_LABELS[option] ?? option}</span>
                    </label>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>
      )}

      <section className="activity-record-section">
        <div className="activity-record-section-title">
          <span>{form.visitResult === "MET" ? "3" : "2"}</span>

          <div>
            <h2>활동 결과</h2>

            <p>활동 종료 시각과 특이사항을 기록해주세요.</p>
          </div>
        </div>

        <div className="activity-record-fields">
          <label>
            <span>활동 완료 시각</span>

            <input
              type="datetime-local"
              name="completedAt"
              value={form.completedAt}
              disabled={!editable}
              onChange={handleFormChange}
            />
          </label>

          <label>
            <span>
              특이사항
              {form.visitResult === "NOT_MET" && <em> 필수</em>}
            </span>

            <textarea
              name="specialNote"
              value={form.specialNote}
              disabled={!editable}
              rows={5}
              placeholder={
                form.visitResult === "NOT_MET"
                  ? "대상자를 만나지 못한 상황을 입력해주세요."
                  : "특이사항이 있다면 입력해주세요."
              }
              onChange={handleFormChange}
            />
          </label>
        </div>
      </section>

      {form.visitResult === "MET" && (
        <section className="activity-record-section">
          <div className="activity-record-section-title">
            <span>4</span>

            <div>
              <h2>대상자 서명</h2>

              <p>활동 내용을 확인한 대상자에게 직접 서명을 받아주세요.</p>
            </div>
          </div>

          {record.signatureUploaded ? (
            <div className="activity-record-signature-status">
              ✓ 서명이 등록되어 있습니다.
              {editable && (
                <span>잘못 등록한 경우 새 이미지로 교체할 수 있습니다.</span>
              )}
            </div>
          ) : (
            <div className="activity-record-signature-status">
              아직 서명이 등록되지 않았습니다.
            </div>
          )}

          {editable && (
            <div className="activity-record-signature-upload">
              <canvas
                ref={signatureCanvasRef}
                className="activity-record-signature-canvas"
                onPointerDown={handleSignaturePointerDown}
                onPointerMove={handleSignaturePointerMove}
                onPointerUp={handleSignaturePointerEnd}
                onPointerCancel={handleSignaturePointerEnd}
              />

              <div className="activity-record-signature-buttons">
                <button
                  type="button"
                  disabled={uploadingSignature || !hasSignatureDrawing}
                  onClick={clearSignatureCanvas}
                >
                  다시 쓰기
                </button>

                <button
                  type="button"
                  disabled={uploadingSignature || !hasSignatureDrawing}
                  onClick={handleSignatureUpload}
                >
                  {uploadingSignature
                    ? "등록 중..."
                    : record.signatureUploaded
                      ? "서명 교체"
                      : "서명 등록"}
                </button>
              </div>

              {/* 서명 등록/교체 결과는 서명 영역 바로 아래에 표시합니다. */}
              {messageTarget === "signature" && actionError && (
                <div className="activity-record-message error">
                  {actionError}
                </div>
              )}

              {messageTarget === "signature" && successMessage && (
                <div className="activity-record-message success">
                  {successMessage}
                </div>
              )}

              <small>
                서명란에 손가락, 마우스 또는 터치펜으로 직접 서명한 뒤 서명 등록
                버튼을 눌러주세요.
              </small>
            </div>
          )}
        </section>
      )}

      {editable ? (
        <section className="activity-record-actions">
          {/* 임시저장 / 최종 제출 결과는 하단 버튼 영역에 표시합니다. */}
          {messageTarget === "actions" && actionError && (
            <div className="activity-record-message error">{actionError}</div>
          )}

          {messageTarget === "actions" && successMessage && (
            <div className="activity-record-message success">
              {successMessage}
            </div>
          )}

          <button
            type="button"
            disabled={saving || submitting || uploadingSignature}
            onClick={handleSaveDraft}
          >
            {saving ? "저장 중..." : "임시저장"}
          </button>

          <button
            type="button"
            className="activity-record-submit"
            disabled={saving || submitting || uploadingSignature}
            onClick={handleSubmit}
          >
            {submitting
              ? "제출 중..."
              : record.reviewStatus === "NEEDS_REVISION"
                ? "수정 내용 재제출"
                : "최종 제출"}
          </button>
        </section>
      ) : (
        <section className="activity-record-readonly">
          <strong>
            {REVIEW_STATUS_LABELS[record.reviewStatus] ?? record.reviewStatus}
          </strong>

          <p>제출된 활동기록은 조회만 할 수 있습니다.</p>
        </section>
      )}
    </main>
  );
}

export default ActivityRecord;
