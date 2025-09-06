package com.example.backend.scheduleItem.repository;

import com.example.backend.scheduleItem.entity.ScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, UUID> {
    List<ScheduleItem> findAllByScheduleId_ScheduleId(UUID scheduleId);

    @Modifying// 👈 SELECT 쿼리가 아님을 명시합니다.
    void deleteAllByScheduleId_ScheduleId(UUID scheduleId);
}
