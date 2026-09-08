package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.Notice;
import com.dagachi.backend.domain.enums.NoticeStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    // 관리자 공지 상태별 목록 조회 기능
    Page<Notice> findByStatus(NoticeStatus status, Pageable pageable);
    // 관리자 공지 삭제 여부별 목록 조회 기능
    Page<Notice> findByDeleted(Boolean deleted, Pageable pageable);
    // 관리자 공지 상태 및 삭제 여부별 목록 조회 기능
    Page<Notice> findByStatusAndDeleted(NoticeStatus status, Boolean deleted, Pageable pageable);

    // 삭제되지 않은 공지 단건 조회 기능
    Optional<Notice> findByIdAndDeletedFalse(Long id);
}
