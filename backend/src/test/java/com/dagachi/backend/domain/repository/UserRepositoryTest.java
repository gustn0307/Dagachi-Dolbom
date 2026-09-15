package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.testsupport.PostgresContainerTestBase;
import org.junit.jupiter.api.Test;
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
}