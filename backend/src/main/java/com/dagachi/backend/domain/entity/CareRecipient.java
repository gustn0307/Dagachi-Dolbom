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

 @Column(name = "deleted_at")
 private LocalDateTime deletedAt;

 /**
  * 신규 돌봄 대상자 생성.
  */
 public static CareRecipient create(
         Institution institution,
         String name,
         UserGender gender,
         Integer birthYear,
         String phone,
         String address,
         String detailAddress,
         BigDecimal latitude,
         BigDecimal longitude,
         ConsentStatus consentStatus
 ) {
  CareRecipient recipient = new CareRecipient();

  recipient.institution = institution;
  recipient.name = name;
  recipient.gender = gender;
  recipient.birthYear = birthYear;
  recipient.phone = phone;
  recipient.address = address;
  recipient.detailAddress = detailAddress;
  recipient.latitude = latitude;
  recipient.longitude = longitude;

  recipient.status = CareRecipientStatus.ACTIVE;
  recipient.consentStatus = consentStatus;

  recipient.consentAt =
          consentStatus == ConsentStatus.AGREED
                  ? LocalDateTime.now()
                  : null;

  recipient.consentWithdrawnAt = null;
  recipient.lastCheckedAt = null;
  recipient.deleted = false;
  recipient.deletedAt = null;

  return recipient;
 }
 /**
  * CARE-04 돌봄 대상자의 기본정보를 수정한다.
  *
  * 기관, 관리 상태, 동의 상태, 최근 확인일은
  * 이 메서드에서 변경하지 않는다.
  */
 public void updateInformation(
         String name,
         UserGender gender,
         Integer birthYear,
         String phone,
         String address,
         String detailAddress,
         BigDecimal latitude,
         BigDecimal longitude
 ) {
  this.name = name;
  this.gender = gender;
  this.birthYear = birthYear;
  this.phone = phone;
  this.address = address;
  this.detailAddress = detailAddress;
  this.latitude = latitude;
  this.longitude = longitude;
 }
 /**
  * CARE-05 돌봄 대상자의 동의 상태를 변경한다.
  */
 public void changeConsentStatus(
         ConsentStatus newConsentStatus
 ) {
  switch (newConsentStatus) {
   case AGREED -> {
    // 처음 동의하거나 철회 후 다시 동의하는 경우
    // 현재 시각을 새로운 동의 시각으로 기록한다.
    if (this.consentStatus != ConsentStatus.AGREED) {
     this.consentAt = LocalDateTime.now();
    }

    this.consentStatus = ConsentStatus.AGREED;
    this.consentWithdrawnAt = null;
   }

   case WITHDRAWN -> {
    // 기존 동의 시각은 이력으로 유지한다.
    this.consentStatus = ConsentStatus.WITHDRAWN;
    this.consentWithdrawnAt = LocalDateTime.now();
   }

   case PENDING -> {
    // 동의 절차를 처음부터 다시 진행한다.
    this.consentStatus = ConsentStatus.PENDING;
    this.consentAt = null;
    this.consentWithdrawnAt = null;
   }
  }
 }
 /**
  * CARE-06 돌봄 대상자 관리를 종료한다.
  *
  * 대상자를 실제 삭제하지 않고 관리 상태만 INACTIVE로 변경한다.
  */
 public void closeManagement() {
  this.status = CareRecipientStatus.INACTIVE;
 }
 /**
  * CARE-07 종료된 돌봄 대상자의 관리를 재개한다.
  *
  * 기존 대상자를 새로 등록하지 않고 관리 상태만 ACTIVE로 변경한다.
  * 기존 제보, 활동, 동의 상태, 최근 확인일은 그대로 유지한다.
  */
 public void reopenManagement() {
  this.status = CareRecipientStatus.ACTIVE;
 }
}
