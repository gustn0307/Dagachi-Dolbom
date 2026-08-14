package com.dagachi.backend.domain.entity;

import com.dagachi.backend.common.entity.BaseTimeEntity;
import com.dagachi.backend.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "institutions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Institution extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false,length=150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=50)
    private InstitutionType type;

    @Column(length=255)
    private String address;

    @Column(length=30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false,length=30)
    private InstitutionStatus status;

    @Column(name="is_deleted",nullable=false)
    private Boolean deleted;

    @Column(name="deleted_at")
    private LocalDateTime deletedAt;
}
