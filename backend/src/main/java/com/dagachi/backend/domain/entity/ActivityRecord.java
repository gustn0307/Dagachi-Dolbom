package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseTimeEntity;
import com.dagachi.backend.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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

 // 공동 Draft의 활동 결과 내용을 수정합니다.
 public void updateDraft(
         VisitResult visitResult,
         LocalDateTime completedAt,
         String specialNote
 ) {
  this.visitResult = visitResult;
  this.completedAt = completedAt;
  this.specialNote = specialNote;
 }

 // 대상자 서명 파일과 서명 시각을 함께 변경합니다.
 public void updateSignature(
         String signatureS3Key,
         LocalDateTime signedAt
 ) {
  this.signatureS3Key = signatureS3Key;
  this.signedAt = signedAt;
 }

 // 활동 결과를 최종 제출 상태로 변경합니다.
 public void submit(User submittedBy) {
  this.submittedBy = submittedBy;
  this.reviewStatus = ActivityReviewStatus.SUBMITTED;
 }

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

    /**
     * 제출된 활동기록을 기관 담당자가 승인한다.
     */
    public void approveReview(
            User reviewedBy
    ) {
        if (
                this.reviewStatus !=
                        ActivityReviewStatus.SUBMITTED
        ) {
            throw new IllegalStateException(
                    "SUBMITTED 상태의 활동기록만 승인할 수 있습니다."
            );
        }

        this.reviewStatus =
                ActivityReviewStatus.APPROVED;

        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNote = null;
    }

    /**
     * 제출된 활동기록에 기관 담당자가 보완을 요청한다.
     */
    public void requestRevision(
            User reviewedBy,
            String reviewNote
    ) {
        if (
                this.reviewStatus !=
                        ActivityReviewStatus.SUBMITTED
        ) {
            throw new IllegalStateException(
                    "SUBMITTED 상태의 활동기록만 보완 요청할 수 있습니다."
            );
        }

        this.reviewStatus =
                ActivityReviewStatus.NEEDS_REVISION;

        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNote = reviewNote;
    }
}