package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseCreatedEntity; import com.dagachi.backend.domain.enums.*; import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="follow_up_actions") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class FollowUpAction extends BaseCreatedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recipient_id",nullable=false) private CareRecipient recipient;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="activity_record_id") private ActivityRecord activityRecord;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="institution_id",nullable=false) private Institution institution;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="created_by",nullable=false) private User createdBy;
 @Enumerated(EnumType.STRING) @Column(name="action_type",nullable=false,length=50) private FollowUpActionType actionType;
 @Column(columnDefinition="text") private String content;
}
