package com.dagachi.backend.domain.entity;

import com.dagachi.backend.common.entity.BaseTimeEntity; import com.dagachi.backend.domain.enums.*; import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity
@Table(name="users")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {
 @Id
 @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;

 @Column(nullable=false,unique=true,length=255)
 private String email;

 @Column(nullable=false,length=255)
 private String password;

 @Column(nullable=false,length=100)
 private String name;

 @Column(length=100)
 private String nickname;

 @Column(nullable=false,length=30)
 private String phone;

 @Enumerated(EnumType.STRING)
 @Column(nullable=false,length=20)
 private UserGender gender;

 @Enumerated(EnumType.STRING)
 @Column(nullable=false,length=30)
 private UserRole role;

 @ManyToOne(fetch=FetchType.LAZY)
 @JoinColumn(name="institution_id")
 private Institution institution;

 @Enumerated(EnumType.STRING)
 @Column(nullable=false,length=30)
 private UserStatus status;

 @Column(name="is_deleted",nullable=false)
 private Boolean deleted;

 @Column(name="deleted_at")
 private LocalDateTime deletedAt;
}
