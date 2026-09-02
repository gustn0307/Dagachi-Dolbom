package com.dagachi.backend.institution.report.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.repository.ReportRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.report.dto.ReportAssignmentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstitutionReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    public InstitutionReportService(
            ReportRepository reportRepository,
            UserRepository userRepository
    ) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
    }

    /**
     * 미배정 제보를 현재 로그인한 기관 사용자의 소속 기관에 배정합니다.
     *
     * 처리 흐름:
     *
     * 로그인 userId
     * -> User 조회
     * -> User.institution 확인
     * -> Report를 DB lock과 함께 조회
     * -> 아직 미배정인지 확인
     * -> Report.institution 지정
     *
     * 비관적 잠금을 사용하므로 여러 기관이 같은 제보를
     * 거의 동시에 관할 지정하더라도 한 기관만 성공합니다.
     */
    @Transactional
    public ReportAssignmentResponse assignReportToMyInstitution(
            Long userId,
            Long reportId
    ) {

        /*
         * JWT에서 전달받은 userId를 기준으로 현재 사용자를 조회합니다.
         *
         * Controller에서 institutionId를 직접 받지 않으므로
         * 다른 기관 ID를 조작해서 요청하는 것을 방지합니다.
         */
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        /*
         * INSTITUTION Role이라도 DB에 실제 소속 기관이 없다면
         * 정상적인 기관 업무를 수행할 수 없으므로 접근을 거부합니다.
         */
        Institution institution = user.getInstitution();

        if (institution == null) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        /*
         * 같은 미배정 Report를 여러 기관이 동시에 가져가는 것을 막기 위해
         * PESSIMISTIC_WRITE lock으로 조회합니다.
         *
         * 이 메서드는 @Transactional 안에서 실행되어야
         * DB lock이 정상적으로 유지됩니다.
         */
        Report report = reportRepository.findWithLockById(reportId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );

        /*
         * 이미 다른 기관이 관할 지정한 제보는 다시 가져갈 수 없습니다.
         */
        if (report.getInstitution() != null) {
            throw new CustomException(
                    ErrorCode.REPORT_ALREADY_ASSIGNED
            );
        }

        /*
         * Entity의 비즈니스 메서드를 통해 소속 기관을 지정합니다.
         *
         * JPA Dirty Checking으로 Transaction 종료 시 UPDATE가 실행되므로
         * 별도의 reportRepository.save(report)는 필요하지 않습니다.
         */
        report.assignInstitution(institution);

        return ReportAssignmentResponse.from(report);
    }
}