package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.Notice;
import com.dagachi.backend.domain.enums.NoticeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long>{
    Page<Notice> findByStatus(NoticeStatus status, Pageable pageable);
    Page<Notice> findByDeleted(Boolean deleted, Pageable pageable);
    Page<Notice> findByStatusAndDeleted(NoticeStatus status, Boolean deleted, Pageable pageable);
}
