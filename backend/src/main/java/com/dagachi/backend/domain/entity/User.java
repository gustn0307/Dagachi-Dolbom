package com.dagachi.backend.domain.entity;

import com.dagachi.backend.common.entity.BaseTimeEntity;
import com.dagachi.backend.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String nickname;

    @Column(nullable = false, length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserGender gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "is_deleted", nullable = false)
    private Boolean deleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static User create(
            String email,
            String password,
            String name,
            String nickname,
            String phone,
            UserGender gender
    ) {
        User user = new User();

        user.email = email;
        user.password = password;
        user.name = name;
        user.nickname = nickname;
        user.phone = phone;
        user.gender = gender;
        user.role = UserRole.USER;
        user.status = UserStatus.ACTIVE;
        user.deleted = false;

        return user;
    }

    // USER-02: 닉네임/전화번호만 변경 가능. null이면 기존 값 유지
    public void updateProfile(String nickname, String phone) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (phone != null) {
            this.phone = phone;
        }
    }

    // 비밀번호 변경. 반드시 암호화된 값을 받는다 (평문 저장 금지)
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // USER-03: [팀 미확정 정책 임시 적용] Soft Delete + WITHDRAWN 처리
    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
