package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseTimeEntity; import com.dagachi.backend.domain.enums.*; import jakarta.persistence.*; import lombok.*; import java.time.LocalDateTime;
@Entity
@Table(name="notices")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {
 @Id
 @GeneratedValue(strategy=GenerationType.IDENTITY)
 private Long id;

 @Column(nullable=false,length=255)
 private String title;

 @Column(nullable=false,columnDefinition="text")
 private String content;

 @ManyToOne(fetch=FetchType.LAZY,optional=false)
 @JoinColumn(name="author_id",nullable=false)
 private User author;

 @Enumerated(EnumType.STRING)
 @Column(nullable=false,length=30)
 private NoticeStatus status;

 @Column(name="is_deleted",nullable=false)
 private Boolean deleted;

 @Column(name="deleted_at")
 private LocalDateTime deletedAt;
}
