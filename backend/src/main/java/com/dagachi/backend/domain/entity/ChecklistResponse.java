package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseTimeEntity; import jakarta.persistence.*; import lombok.*;
@Entity @Table(name="checklist_responses",uniqueConstraints=@UniqueConstraint(name="uq_checklist_responses_record_item",columnNames={"activity_record_id","checklist_item_id"})) @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ChecklistResponse extends BaseTimeEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="activity_record_id",nullable=false) private ActivityRecord activityRecord;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="checklist_item_id",nullable=false) private ChecklistItem checklistItem;
 @Column(name="selected_value",length=500) private String selectedValue; @Column(name="text_value",columnDefinition="text") private String textValue;
}
