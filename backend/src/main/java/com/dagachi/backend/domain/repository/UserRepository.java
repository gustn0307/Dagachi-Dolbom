package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 로그인용
    Optional<User> findByEmailAndDeletedFalse(String email);

    // 회원가입 이메일 중복 검사
    boolean existsByEmailAndDeletedFalse(String email);
}