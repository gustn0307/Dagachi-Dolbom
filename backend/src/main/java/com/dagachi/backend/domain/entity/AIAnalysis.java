package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseCreatedEntity; import com.dagachi.backend.domain.enums.*; import com.fasterxml.jackson.databind.JsonNode; import jakarta.persistence.*; import lombok.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
@Entity
@Table(name="ai_analyses")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AIAnalysis extends BaseCreatedEntity {
 @Id
 @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;

 @Enumerated(EnumType.STRING)
 @Column(name="analysis_type",nullable=false,length=50)
 private AIAnalysisType analysisType;

 @Enumerated(EnumType.STRING)
 @Column(name="target_type",nullable=false,length=30)
 private AITargetType targetType;

 @Column(name="target_id",nullable=false)
 private Long targetId;

 @JdbcTypeCode(SqlTypes.JSON)
 @Column(name="result_json",nullable=false,columnDefinition="jsonb")
 private JsonNode resultJson;

 @Column(name="model_name",length=100)
 private String modelName;
}
