package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChecklistItemRepository
        extends JpaRepository<ChecklistItem, Long> {

    // 특정 체크리스트 버전의 문항을 화면 표시 순서대로 조회한다.
    List<ChecklistItem> findByVersionOrderBySortOrderAsc(
            Integer version
    );

    /**
     * 현재 사용 중인(active=true) 체크리스트의 최신 버전 번호.
     * CHECK-01(맹동영 담당)에서도 재사용할 수 있도록 공용 Repository에 둔다.
     */
    @Query("SELECT MAX(ci.version) FROM ChecklistItem ci WHERE ci.active = true")
    Optional<Integer> findCurrentActiveVersion();
}