package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 로그인용
    Optional<User> findByEmailAndDeletedFalse(String email);

    // 탈퇴하지 않은 사용자 이메일 존재 여부
    boolean existsByEmailAndDeletedFalse(String email);

    // 회원가입 중복 검사용
    // Soft Delete된 계정도 동일 이메일 재가입을 허용하지 않으므로
    // deleted 여부와 관계없이 전체 사용자에서 이메일 중복을 확인합니다.
    boolean existsByEmail(String email);

    // 현재 로그인 사용자 조회
    Optional<User> findByIdAndDeletedFalse(Long id);

    // 메인페이지 통계 조회
    long countByRoleAndStatusAndDeletedFalse(UserRole role, UserStatus status);
}