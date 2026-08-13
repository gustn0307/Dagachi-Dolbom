package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseTimeEntity; import com.dagachi.backend.domain.enums.*; import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity @Table(name="care_activities") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CareActivity extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recipient_id",nullable=false) private CareRecipient recipient;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="institution_id",nullable=false) private Institution institution;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="created_by",nullable=false) private User createdBy;
 @Column(name="scheduled_at",nullable=false) private LocalDateTime scheduledAt; @Column(name="required_people",nullable=false) private Integer requiredPeople;
 @Enumerated(EnumType.STRING) @Column(name="gender_condition",nullable=false,length=30) private GenderCondition genderCondition;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ActivityStatus status;
}
