package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseTimeEntity; import com.dagachi.backend.domain.enums.*; import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity
@Table(name="activity_applications",uniqueConstraints=@UniqueConstraint(name="uq_activity_applications_activity_user",columnNames={"activity_id","user_id"}))
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ActivityApplication extends BaseTimeEntity {
 @Id
 @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;

 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="activity_id",nullable=false)
 private CareActivity activity;

 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="user_id",nullable=false)
 private User user;

 @Enumerated(EnumType.STRING)
 @Column(name="application_type",nullable=false,length=30)
 private ApplicationType applicationType;

 @Enumerated(EnumType.STRING)
 @Column(nullable=false,length=30)
 private ApplicationStatus status;

 @ManyToOne(fetch=FetchType.LAZY)
 @JoinColumn(name="approved_by")
 private User approvedBy;

 @Column(name="approved_at")
 private LocalDateTime approvedAt;

 @Column(name="rejected_reason",columnDefinition="text")
 private String rejectedReason;

 // Entity 확인 및 수정
 public static ActivityApplication createDirect(CareActivity activity, User user) {
  ActivityApplication application = new ActivityApplication();
  application.activity = activity;
  application.user = user;
  application.applicationType = ApplicationType.DIRECT;
  application.status = ApplicationStatus.PENDING;
  return application;
 }

 public void reactivate() {
  if (this.status != ApplicationStatus.CANCELED) {
   throw new IllegalStateException("CANCELED 상태에서만 재신청할 수 있습니다.");
  }
  this.status = ApplicationStatus.PENDING;
  this.approvedBy = null;
  this.approvedAt = null;
  this.rejectedReason = null;
 }

 public void cancel() {
  if (this.status != ApplicationStatus.PENDING && this.status != ApplicationStatus.APPROVED) {
   throw new IllegalStateException("PENDING 또는 APPROVED 상태에서만 취소할 수 있습니다.");
  }
  this.status = ApplicationStatus.CANCELED;
 }
}
