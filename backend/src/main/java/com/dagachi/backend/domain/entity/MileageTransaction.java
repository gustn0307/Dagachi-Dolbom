package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseCreatedEntity; import com.dagachi.backend.domain.enums.*; import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="mileage_transactions") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MileageTransaction extends BaseCreatedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="activity_record_id") private ActivityRecord activityRecord;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private MileageTransactionType type;
 @Column(nullable=false) private Integer amount; @Column(length=255) private String reason;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="created_by") private User createdBy;
}
