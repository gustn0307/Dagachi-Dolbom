package com.dagachi.backend.domain.entity;

import com.dagachi.backend.common.entity.BaseTimeEntity;
import com.dagachi.backend.domain.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 private Long id;

 // 회원 제보일 경우 로그인한 사용자를 저장합니다.
 // 비회원 제보는 reporter = null로 저장합니다.
 @ManyToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "reporter_id")
 private User reporter;

 // 기관에서 제보를 돌봄 대상자와 연결하기 전까지는 null입니다.
 @ManyToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "care_recipient_id")
 private CareRecipient careRecipient;

 // 기관에 배정되기 전까지는 null입니다.
 @ManyToOne(fetch = FetchType.LAZY)
 @JoinColumn(name = "institution_id")
 private Institution institution;

 // 비회원 제보에서 연락 가능한 전화번호를 저장합니다.
 // 회원 제보에서는 null로 저장합니다.
 @Column(name = "guest_phone", length = 30)
 private String guestPhone;

 @Column(nullable = false, columnDefinition = "text")
 private String content;

 @Column(length = 255)
 private String address;

 @Column(precision = 10, scale = 7)
 private BigDecimal latitude;

 @Column(precision = 10, scale = 7)
 private BigDecimal longitude;

 @Enumerated(EnumType.STRING)
 @Column(nullable = false, length = 30)
 private ReportStatus status;

 @JdbcTypeCode(SqlTypes.VECTOR)
 @Column(columnDefinition = "vector")
 private float[] embedding;

 /**
  * 회원 또는 비회원의 신규 제보를 생성합니다.
  *
  * 회원 제보:
  * - reporter: 로그인 사용자
  * - guestPhone: null
  *
  * 비회원 제보:
  * - reporter: null
  * - guestPhone: 비회원 연락처
  *
  * 신규 제보의 최초 상태는 항상 SUBMITTED입니다.
  */
 public static Report create(
         User reporter,
         String guestPhone,
         String content,
         String address,
         BigDecimal latitude,
         BigDecimal longitude
 ) {
  Report report = new Report();

  report.reporter = reporter;
  report.guestPhone = guestPhone;
  report.content = content;
  report.address = address;
  report.latitude = latitude;
  report.longitude = longitude;
  report.status = ReportStatus.SUBMITTED;

  return report;
 }
}