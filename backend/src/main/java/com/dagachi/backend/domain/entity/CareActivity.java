package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseTimeEntity; import com.dagachi.backend.domain.enums.*; import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity
@Table(name="care_activities")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CareActivity extends BaseTimeEntity {
 @Id
 @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;

 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="recipient_id",nullable=false)
 private CareRecipient recipient;

 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="institution_id",nullable=false)
 private Institution institution;

 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="created_by",nullable=false)
 private User createdBy;

 @Column(name="scheduled_at",nullable=false)
 private LocalDateTime scheduledAt;

 @Column(name="required_people",nullable=false)
 private Integer requiredPeople;

 @Enumerated(EnumType.STRING)
 @Column(name="gender_condition",nullable=false,length=30)
 private GenderCondition genderCondition;

 @Enumerated(EnumType.STRING)
 @Column(nullable=false,length=30)
 private ActivityStatus status;

 /**
  * 기관 담당자가 새로운 돌봄 활동을 등록한다.
  */
 public static CareActivity create(
         CareRecipient recipient,
         Institution institution,
         User createdBy,
         LocalDateTime scheduledAt,
         Integer requiredPeople,
         GenderCondition genderCondition
 ) {
  CareActivity activity =
          new CareActivity();

  activity.recipient =
          recipient;

  activity.institution =
          institution;

  activity.createdBy =
          createdBy;

  activity.scheduledAt =
          scheduledAt;

  activity.requiredPeople =
          requiredPeople;

  activity.genderCondition =
          genderCondition;

  // 새 활동은 모집 중 상태로 시작한다.
  activity.status =
          ActivityStatus.RECRUITING;

  return activity;
 }

 /**
  * 기관 담당자가 활동 일정과 필요 인원을 수정한다.
  */
 public void updateInformation(
         LocalDateTime scheduledAt,
         Integer requiredPeople
 ) {
  this.scheduledAt =
          scheduledAt;

  this.requiredPeople =
          requiredPeople;
 }
 /**
  * 활동 상태를 변경한다.
  */
 public void changeStatus(
         ActivityStatus status
 ) {
  this.status = status;
 }


}
