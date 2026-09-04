package com.dagachi.backend.domain.entity;

import com.dagachi.backend.common.entity.BaseTimeEntity;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
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
     * <p>
     * 회원 제보:
     * - reporter: 로그인 사용자
     * - guestPhone: null
     * <p>
     * 비회원 제보:
     * - reporter: null
     * - guestPhone: 비회원 연락처
     * <p>
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

    /**
     * 미배정 제보를 특정 기관의 관할 제보로 지정합니다.
     * <p>
     * institution 필드를 외부 Service에서 직접 변경하지 않고,
     * Report Entity가 자신의 상태 변경을 담당하도록 합니다.
     * <p>
     * 중복 배정 여부와 동시성 제어는 Service/Repository 계층에서
     * 확인한 뒤 이 메서드를 호출합니다.
     */
    public void assignInstitution(
            Institution institution
    ) {
        this.institution = institution;
    }

    /**
     * 기관 담당자의 검토 결과에 따라 제보 처리 상태를 변경합니다.
     *
     * 상태 전이 규칙을 Service에 흩어놓지 않고
     * Report Entity가 자신의 상태 변경 규칙을 관리합니다.
     *
     * 허용 전이:
     * SUBMITTED      -> REVIEWING
     * REVIEWING      -> NEED_MORE_INFO / ACCEPTED / REJECTED
     * NEED_MORE_INFO -> REVIEWING / ACCEPTED / REJECTED
     * ACCEPTED       -> CLOSED
     * REJECTED       -> CLOSED
     * CLOSED         -> 변경 불가
     */
    public void changeStatus(
            ReportStatus newStatus
    ) {
        boolean allowed =
                switch (this.status) {
                    case SUBMITTED ->
                            newStatus == ReportStatus.REVIEWING;

                    case REVIEWING ->
                            newStatus == ReportStatus.NEED_MORE_INFO
                                    || newStatus == ReportStatus.ACCEPTED
                                    || newStatus == ReportStatus.REJECTED;

                    case NEED_MORE_INFO ->
                            newStatus == ReportStatus.REVIEWING
                                    || newStatus == ReportStatus.ACCEPTED
                                    || newStatus == ReportStatus.REJECTED;

                    case ACCEPTED, REJECTED ->
                            newStatus == ReportStatus.CLOSED;

                    case CLOSED ->
                            false;
                };

        if (!allowed) {
            throw new CustomException(
                    ErrorCode.REPORT_INVALID_STATUS_TRANSITION
            );
        }

        this.status = newStatus;
    }

    /**
     * 제보를 기존 돌봄 대상자와 연결합니다.
     *
     * 한 제보에는 최대 한 명의 돌봄 대상자만 연결할 수 있습니다.
     * 기관 일치 여부는 Service에서 검증한 뒤 호출합니다.
     */
    public void linkCareRecipient(
            CareRecipient careRecipient
    ) {
        if (this.careRecipient != null) {
            throw new CustomException(
                    ErrorCode.REPORT_CARE_RECIPIENT_ALREADY_LINKED
            );
        }

        this.careRecipient = careRecipient;
    }
}
