package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistItemRepository
        extends JpaRepository<ChecklistItem, Long> {

    // 특정 체크리스트 버전의 문항을 화면 표시 순서대로 조회한다.
    List<ChecklistItem> findByVersionOrderBySortOrderAsc(
            Integer version
    );
}