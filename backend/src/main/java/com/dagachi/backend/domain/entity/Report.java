package com.dagachi.backend.domain.entity;

import com.dagachi.backend.common.entity.BaseTimeEntity; import com.dagachi.backend.domain.enums.*; import jakarta.persistence.*; import lombok.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes; import java.math.BigDecimal;
@Entity
@Table(name="reports")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {
 @Id
 @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;

 @ManyToOne(fetch=FetchType.LAZY)
 @JoinColumn(name="reporter_id")
 private User reporter;

 @ManyToOne(fetch=FetchType.LAZY)
 @JoinColumn(name="care_recipient_id")
 private CareRecipient careRecipient;

 @ManyToOne(fetch=FetchType.LAZY)
 @JoinColumn(name="institution_id")
 private Institution institution;

 @Column(name="guest_phone",length=30)
 private String guestPhone;

 @Column(nullable=false,columnDefinition="text")
 private String content;

 @Column(length=255)
 private String address;

 @Column(precision=10,scale=7)
 private BigDecimal latitude;

 @Column(precision=10,scale=7)
 private BigDecimal longitude;

 @Enumerated(EnumType.STRING)
 @Column(nullable=false,length=30)
 private ReportStatus status;

 @JdbcTypeCode(SqlTypes.VECTOR)
 @Column(columnDefinition="vector")
 private float[] embedding;
}
