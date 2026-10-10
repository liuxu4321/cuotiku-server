package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.CaptureBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaptureBatchRepository extends JpaRepository<CaptureBatch, String> {
    java.util.List<CaptureBatch> findByUserId(Long userId);
    java.util.List<CaptureBatch> findByUserIdAndStudentId(Long userId, String studentId);
}
