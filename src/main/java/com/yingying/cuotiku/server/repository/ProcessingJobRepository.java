package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, String> {
    java.util.List<ProcessingJob> findByUserId(Long userId);
    java.util.List<ProcessingJob> findByUserIdAndStudentId(Long userId, String studentId);
    java.util.List<ProcessingJob> findByBatchId(String batchId);
}
