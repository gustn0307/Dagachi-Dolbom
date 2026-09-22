package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 로그인용
    Optional<User> findByEmailAndDeletedFalse(String email);

    // 회원가입 이메일 중복 검사
    boolean existsByEmailAndDeletedFalse(String email);

    // 현재 로그인 사용자 조회
    Optional<User> findByIdAndDeletedFalse(Long id);

    // 메인페이지 통계 조회
    long countByRoleAndStatusAndDeletedFalse(UserRole role, UserStatus status);

    /**
     * [수정] 원래 메서드명(findByIdAndDeletedFalseForUpdate)만으로는
     * Spring Data가 자동으로 쿼리를 만들지 못해 기동 시 에러가 났다.
     *
     * "DeletedFalse" 뒤에 "ForUpdate"가 더 붙으면 Spring Data의 Part 파서가
     * "False"를 Boolean 비교 키워드로 인식하지 못하고, "deleted" 프로퍼티
     * 안에 "falseForUpdate"라는 중첩 프로퍼티가 있다고 착각해서 찾다가 실패한다.
     * (PropertyReferenceException: No property 'falseForUpdate' found for
     * type 'Boolean'; Traversed path: User.deleted)
     *
     * @Query를 명시하면 메서드 이름 자동 파싱을 거치지 않으므로 이 문제를
     * 완전히 피할 수 있다. 메서드 이름과 시그니처는 그대로 유지해서
     * 이 메서드를 호출하는 다른 Service 코드는 수정할 필요가 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deleted = false")
    Optional<User> findByIdAndDeletedFalseForUpdate(@Param("id") Long id);
}