package com.dagachi.backend.domain.entity;
import com.dagachi.backend.common.entity.BaseTimeEntity;
import com.dagachi.backend.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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

 // 공지 생성 기능
 public static Notice create(
         String title,
         String content,
         User author
 ) {
  Notice notice = new Notice();

  notice.title = title;
  notice.content = content;
  notice.author = author;
  notice.status = NoticeStatus.DRAFT;
  notice.deleted = false;

  return notice;
 }

 // 공지 제목 및 내용 수정 기능
 public void update(String title, String content) {
  if (title != null) {
   this.title = title;
  }

  if (content != null) {
   this.content = content;
  }
 }

 // 공지 상태 변경 기능
 public void changeStatus(NoticeStatus status) {
  this.status = status;
 }

 // 공지 Soft Delete 기능
 public void softDelete() {
  this.deleted = true;
  this.deletedAt = LocalDateTime.now();
 }
}

