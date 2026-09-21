package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.testsupport.PostgresContainerTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserRepository가 실제 PostgreSQL + pgvector 테스트 DB에서
 * 정상적으로 동작하는지 확인하는 Repository 통합 테스트입니다.
 *
 * Testcontainers 설정은 PostgresContainerTestBase에서 공통으로 제공합니다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class UserRepositoryTest
        extends PostgresContainerTestBase {

    @Autowired
    private UserRepository userRepository;

    /**
     * 삭제되지 않은 사용자는 email 조건으로 정상 조회되는지 확인합니다.
     *
     * 검증 대상:
     * UserRepository.findByEmailAndDeletedFalse()
     */
    @Test
    void findByEmailAndDeletedFalse_삭제되지않은사용자는_조회된다() {

        // given
        User user = User.create(
                "test@example.com",
                "encoded-password",
                "테스트 사용자",
                "테스터",
                "01012345678",
                UserGender.MALE
        );

        userRepository.save(user);

        // when
        Optional<User> result =
                userRepository.findByEmailAndDeletedFalse(
                        "test@example.com"
                );

        // then
        assertThat(result).isPresent();

        assertThat(result.get().getEmail())
                .isEqualTo("test@example.com");

        assertThat(result.get().getDeleted())
                .isFalse();
    }

    /**
     * withdraw()로 Soft Delete 처리된 사용자는
     * findByEmailAndDeletedFalse() 조회 대상에서 제외되는지 확인합니다.
     *
     * 검증 대상:
     * User.withdraw()
     * UserRepository.findByEmailAndDeletedFalse()
     */
    @Test
    void findByEmailAndDeletedFalse_탈퇴한사용자는_조회되지않는다() {

        // given
        User user = User.create(
                "withdrawn@example.com",
                "encoded-password",
                "탈퇴 사용자",
                "탈퇴테스터",
                "01098765432",
                UserGender.FEMALE
        );

        user.withdraw();

        userRepository.save(user);

        // when
        Optional<User> result =
                userRepository.findByEmailAndDeletedFalse(
                        "withdrawn@example.com"
                );

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("REQ-AUTH-01, REQ-AUTH-02 - Soft Delete된 계정 이메일도 전체 중복 검사에서는 존재한다")
    void existsByEmail_탈퇴한사용자이메일도_중복으로_조회된다() {

        // given
        User user = User.create(
                "withdrawn-duplicate@example.com",
                "encoded-password",
                "탈퇴 중복 테스트",
                "탈퇴중복",
                "01011112222",
                UserGender.MALE
        );

        /*
         * 실제 회원탈퇴와 동일하게
         * status=WITHDRAWN, deleted=true, deletedAt 설정
         */
        user.withdraw();

        userRepository.saveAndFlush(user);

        // when
        boolean existsIncludingDeleted =
                userRepository.existsByEmail(
                        "withdrawn-duplicate@example.com"
                );

        boolean existsOnlyActive =
                userRepository.existsByEmailAndDeletedFalse(
                        "withdrawn-duplicate@example.com"
                );

        // then
        /*
         * 회원가입 중복 검사:
         * Soft Delete 여부와 관계없이 기존 email이면 true
         */
        assertThat(existsIncludingDeleted).isTrue();

        /*
         * 로그인/현재 활성 사용자 조회 관점:
         * Soft Delete된 사용자는 제외되므로 false
         */
        assertThat(existsOnlyActive).isFalse();
    }
}