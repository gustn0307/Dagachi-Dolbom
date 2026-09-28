package com.dagachi.backend.institution.profile.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.InstitutionStatus;
import com.dagachi.backend.domain.enums.InstitutionType;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.profile.dto.InstitutionProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * InstitutionProfileService(GET /api/institution/profile) 단위 테스트.
 *
 * Institution의 InstitutionType/InstitutionStatus 실제 enum 값 목록은 알 수 없으므로,
 * 어떤 값이든 상관없이 values()[0]으로 첫 번째 상수를 그대로 사용한다.
 */
@ExtendWith(MockitoExtension.class)
class InstitutionProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private InstitutionProfileService institutionProfileService;

    private static final Long USER_ID = 1L;

    private User buildUser() {
        return User.create(
                "institution-user@test.com", "encoded-pw", "담당자",
                "닉네임", "010-0000-0000", UserGender.MALE
        );
    }

    @Test
    @DisplayName("getMyInstitutionProfile - 사용자가 없으면 USER_NOT_FOUND")
    void getMyInstitutionProfile_사용자가_없으면_예외를_던진다() {
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> institutionProfileService.getMyInstitutionProfile(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("getMyInstitutionProfile - 소속 기관이 없으면 FORBIDDEN (USER/ADMIN 등)")
    void getMyInstitutionProfile_소속기관이_없으면_예외를_던진다() {
        User user = buildUser(); // institution 미설정 -> null
        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> institutionProfileService.getMyInstitutionProfile(USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("getMyInstitutionProfile - 정상이면 소속 기관 정보를 반환한다")
    void getMyInstitutionProfile_정상이면_기관정보를_반환한다() {
        User user = buildUser();

        Institution institution = mock(Institution.class);
        given(institution.getId()).willReturn(10L);
        given(institution.getName()).willReturn("행복복지관");
        given(institution.getType()).willReturn(InstitutionType.values()[0]);
        given(institution.getAddress()).willReturn("서울시 강남구 테헤란로 1");
        given(institution.getPhone()).willReturn("02-1234-5678");
        given(institution.getStatus()).willReturn(InstitutionStatus.values()[0]);
        ReflectionTestUtils.setField(user, "institution", institution);

        given(userRepository.findByIdAndDeletedFalse(USER_ID)).willReturn(Optional.of(user));

        InstitutionProfileResponse response = institutionProfileService.getMyInstitutionProfile(USER_ID);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("행복복지관");
        assertThat(response.address()).isEqualTo("서울시 강남구 테헤란로 1");
        assertThat(response.phone()).isEqualTo("02-1234-5678");
        assertThat(response.type()).isEqualTo(InstitutionType.values()[0].name());
        assertThat(response.status()).isEqualTo(InstitutionStatus.values()[0]);
    }
}