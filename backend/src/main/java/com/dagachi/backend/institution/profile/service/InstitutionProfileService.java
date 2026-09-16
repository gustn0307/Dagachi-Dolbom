package com.dagachi.backend.institution.profile.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.profile.dto.InstitutionProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InstitutionProfileService {

    private final UserRepository userRepository;

    public InstitutionProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public InstitutionProfileResponse getMyInstitutionProfile(Long userId) {
        // 삭제되지 않은 로그인 사용자 조회
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // INSTITUTION Role이 아니거나 소속 기관이 없으면 접근 차단
        // (팀 가이드 8번: role=INSTITUTION이면 institution != null이 Service Layer 규칙)
        Institution institution = user.getInstitution();
        if (institution == null) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return InstitutionProfileResponse.from(institution);
    }
}