package com.dagachi.backend.domain.entity;

import com.dagachi.backend.common.entity.BaseTimeEntity; import com.dagachi.backend.domain.enums.*; import jakarta.persistence.*; import lombok.*; import java.math.BigDecimal; import java.time.LocalDateTime;
@Entity
@Table(name="care_recipients")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class CareRecipient extends BaseTimeEntity {
 @Id
 @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;

 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="institution_id",nullable=false)
 private Institution institution;

 @Column(nullable=false,length=100)
 private String name;

 @Enumerated(EnumType.STRING)
 @Column(nullable=false,length=20)
 private UserGender gender;

 @Column(name="birth_year")
 private Integer birthYear;

 @Column(length=30)
 private String phone;

 @Column(nullable=false,length=255)
 private String address;

 @Column(name="detail_address",length=255)
 private String detailAddress;

 @Column(precision=10,scale=7)
 private BigDecimal latitude;

 @Column(precision=10,scale=7)
 private BigDecimal longitude;

 @Enumerated(EnumType.STRING)
 @Column(nullable=false,length=30)
 private CareRecipientStatus status;

 @Enumerated(EnumType.STRING)
 @Column(name="consent_status",nullable=false,length=30)
 private ConsentStatus consentStatus;

 @Column(name="consent_at")
 private LocalDateTime consentAt;

 @Column(name="consent_withdrawn_at")
 private LocalDateTime consentWithdrawnAt;

 @Column(name="last_checked_at")
 private LocalDateTime lastCheckedAt;

 @Column(name="is_deleted",nullable=false)
 private Boolean deleted;

 @Column(name="deleted_at")
 private LocalDateTime deletedAt;
}
