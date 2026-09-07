package com.dagachi.backend.domain.entity;

import com.dagachi.backend.common.entity.BaseCreatedEntity;
import com.dagachi.backend.domain.enums.AIAnalysisType;
import com.dagachi.backend.domain.enums.AITargetType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AIAnalysis extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 50)
    private AIAnalysisType analysisType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private AITargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode resultJson;

    @Column(name = "model_name", length = 100)
    private String modelName;


    /**
     * AI 분석 결과를 생성할 때 사용하는 정적 팩토리 메서드입니다.
     * <p>
     * Entity 필드를 외부에서 setter로 하나씩 변경하지 않고,
     * 생성에 필요한 값들을 한 번에 전달하도록 합니다.
     * <p>
     * 현재 AIAnalysis는 여러 분석 타입에서 공통으로 사용되므로
     * REPORT_SUMMARY뿐 아니라 이후 DUPLICATE_REPORT,
     * PRIORITY_CANDIDATE 등에서도 재사용할 수 있습니다.
     */
    public static AIAnalysis create(
            AIAnalysisType analysisType,
            AITargetType targetType,
            Long targetId,
            JsonNode resultJson,
            String modelName
    ) {
        AIAnalysis analysis = new AIAnalysis();

        analysis.analysisType = analysisType;
        analysis.targetType = targetType;
        analysis.targetId = targetId;
        analysis.resultJson = resultJson;
        analysis.modelName = modelName;

        return analysis;
    }
}
