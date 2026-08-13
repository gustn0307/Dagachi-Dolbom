package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseTimeEntity; import com.dagachi.backend.domain.enums.*; import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity @Table(name="activity_records") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ActivityRecord extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="activity_id",nullable=false,unique=true) private CareActivity activity;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="submitted_by") private User submittedBy;
 @Column(name="checklist_version",nullable=false) private Integer checklistVersion;
 @Enumerated(EnumType.STRING) @Column(name="visit_result",length=30) private VisitResult visitResult;
 @Column(name="started_at") private LocalDateTime startedAt; @Column(name="completed_at") private LocalDateTime completedAt;
 @Column(name="special_note",columnDefinition="text") private String specialNote; @Column(name="signature_s3_key",length=500) private String signatureS3Key;
 @Column(name="signed_at") private LocalDateTime signedAt;
 @Enumerated(EnumType.STRING) @Column(name="review_status",nullable=false,length=30) private ActivityReviewStatus reviewStatus;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewed_by") private User reviewedBy;
 @Column(name="reviewed_at") private LocalDateTime reviewedAt; @Column(name="review_note",columnDefinition="text") private String reviewNote;
}
