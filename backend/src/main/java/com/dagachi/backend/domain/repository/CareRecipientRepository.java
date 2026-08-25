package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.enums.CareRecipientStatus;
import com.dagachi.backend.domain.enums.ConsentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * CareRecipient Entity의 DB 조회를 담당하는 공통 Repository.
 *
 * Repository는 기관 기능 패키지에 중복으로 만들지 않고
 * domain.repository에서 공통으로 관리한다.
 *
 * 기관 대상자 조회에서는 반드시 institutionId와 deleted=false 조건을
 * 적용하여 다른 기관 또는 삭제된 대상자가 노출되지 않도록 한다.
 */
public interface CareRecipientRepository
        extends JpaRepository<CareRecipient, Long> {

    /**
     * CARE-01 기관별 돌봄 대상자 목록 조회.
     *
     * 기본 조건:
     * - 로그인 담당자의 기관에 속한 대상자만 조회
     * - 논리적으로 삭제되지 않은 대상자만 조회
     *
     * 선택 조건:
     * - status: 대상자 관리 상태
     * - consentStatus: 서비스 참여 동의 상태
     * - keyword: 이름, 기본 주소, 전화번호 통합검색
     *
     * Pagination과 정렬은 Service에서 생성한 Pageable로 전달한다.
     *
     * @param institutionId 로그인 기관 담당자의 소속 기관 ID
     * @param status 대상자 관리 상태 필터
     * @param consentStatus 서비스 참여 동의 상태 필터
     * @param keyword 이름·주소·전화번호 검색어
     * @param pageable 페이지 번호, 크기 및 정렬 조건
     * @return 조건에 맞는 기관 대상자 Page
     */
    @Query("""
            SELECT cr
            FROM CareRecipient cr
            WHERE cr.institution.id = :institutionId
              AND cr.deleted = false

              AND (
                    :status IS NULL
                    OR cr.status = :status
                  )

              AND (
                    :consentStatus IS NULL
                    OR cr.consentStatus = :consentStatus
                  )

              AND (
                    :keyword IS NULL
                    OR :keyword = ''

                    OR LOWER(cr.name)
                       LIKE LOWER(
                           CONCAT('%', :keyword, '%')
                       )

                    OR LOWER(cr.address)
                       LIKE LOWER(
                           CONCAT('%', :keyword, '%')
                       )

                    OR REPLACE(cr.phone, '-', '')
                       LIKE CONCAT(
                           '%',
                           REPLACE(:keyword, '-', ''),
                           '%'
                       )
                  )
            """)
    Page<CareRecipient> findAllByCondition(
            @Param("institutionId")
            Long institutionId,

            @Param("status")
            CareRecipientStatus status,

            @Param("consentStatus")
            ConsentStatus consentStatus,

            @Param("keyword")
            String keyword,

            Pageable pageable
    );

    /**
     * CARE-02 기관별 돌봄 대상자 상세 조회.
     *
     * recipientId만으로 조회하지 않고 institutionId를 함께 확인한다.
     * 따라서 다른 기관 담당자가 대상자 ID를 직접 입력해도 조회되지 않는다.
     *
     * 논리적으로 삭제된 대상자도 상세 조회에서 제외한다.
     *
     * @param recipientId 조회할 돌봄 대상자 ID
     * @param institutionId 로그인 기관 담당자의 소속 기관 ID
     * @return 대상자 ID, 기관 ID, 삭제 여부 조건을 만족하는 대상자
     */
    Optional<CareRecipient>
    findByIdAndInstitution_IdAndDeletedFalse(
            Long recipientId,
            Long institutionId
    );

    @Query("SELECT COUNT(r) FROM Report r WHERE r.careRecipient.id = :recipientId")
    long countReportsByRecipientId(@Param("recipientId") Long recipientId);

    @Query("SELECT COUNT(a) FROM CareActivity a WHERE a.recipient.id = :recipientId")
    long countActivitiesByRecipientId(@Param("recipientId") Long recipientId);
}
