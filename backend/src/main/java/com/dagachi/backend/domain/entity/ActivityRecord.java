package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseTimeEntity; import com.dagachi.backend.domain.enums.*; import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity
@Table(name="activity_records")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ActivityRecord extends BaseTimeEntity {
 @Id
 @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;

 @OneToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="activity_id",nullable=false,unique=true)
 private CareActivity activity;

 @ManyToOne(fetch=FetchType.LAZY)
 @JoinColumn(name="submitted_by")
 private User submittedBy;

 @Column(name="checklist_version",nullable=false)
 private Integer checklistVersion;

 @Enumerated(EnumType.STRING)
 @Column(name="visit_result",length=30)
 private VisitResult visitResult;

 @Column(name="started_at")
 private LocalDateTime startedAt;

 @Column(name="completed_at")
 private LocalDateTime completedAt;

 @Column(name="special_note",columnDefinition="text")
 private String specialNote;

 @Column(name="signature_s3_key",length=500)
 private String signatureS3Key;

 @Column(name="signed_at")
 private LocalDateTime signedAt;

 @Enumerated(EnumType.STRING)
 @Column(name="review_status",nullable=false,length=30)
 private ActivityReviewStatus reviewStatus;

 @ManyToOne(fetch=FetchType.LAZY)
 @JoinColumn(name="reviewed_by")
 private User reviewedBy;

 @Column(name="reviewed_at")
 private LocalDateTime reviewedAt;

 @Column(name="review_note",columnDefinition="text")
 private String reviewNote;

 /**
  * RECORD-01 활동 시작 시 DRAFT 상태의 공동 활동 결과를 생성한다.
  * submittedBy/visitResult 등은 DRAFT에서 아직 확정되지 않으므로 null로 둔다.
  * 이후 값 채우기(제출·검토)는 맹동영님 담당(RECORD-02~09) 메서드에서 처리한다.
  */
 public static ActivityRecord createDraft(
         CareActivity activity,
         Integer checklistVersion,
         LocalDateTime startedAt
 ) {
  ActivityRecord record = new ActivityRecord();

  record.activity = activity;
  record.checklistVersion = checklistVersion;
  record.startedAt = startedAt;
  record.reviewStatus = ActivityReviewStatus.DRAFT;

  return record;
 }
}