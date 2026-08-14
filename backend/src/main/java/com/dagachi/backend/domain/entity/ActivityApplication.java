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
}
