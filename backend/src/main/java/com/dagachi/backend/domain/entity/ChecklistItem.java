package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseTimeEntity; import com.dagachi.backend.domain.enums.*; import com.fasterxml.jackson.databind.JsonNode; import jakarta.persistence.*; import lombok.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes;
@Entity
@Table(
        name="checklist_items",
        uniqueConstraints=@UniqueConstraint(
                name="uq_checklist_items_version_code",
                columnNames={"version","code"}
        )
)
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ChecklistItem extends BaseTimeEntity {
 @Id
 @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;

 @Column(nullable=false)
 private Integer version;

 @Column(nullable=false,length=50)
 private String code;

 @Column(nullable=false,length=500)
 private String question;

 @Enumerated(EnumType.STRING)
 @Column(name="item_type",nullable=false,length=30)
 private ChecklistItemType itemType;

 @JdbcTypeCode(SqlTypes.JSON)
 @Column(name="options_json",columnDefinition="jsonb")
 private JsonNode optionsJson;

 @Column(nullable=false)
 private Boolean required;

 @Column(name="sort_order",nullable=false)
 private Integer sortOrder;

 @Column(nullable=false)
 private Boolean active;
}
