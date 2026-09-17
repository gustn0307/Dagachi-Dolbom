package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.UserRole;
import com.dagachi.backend.domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}