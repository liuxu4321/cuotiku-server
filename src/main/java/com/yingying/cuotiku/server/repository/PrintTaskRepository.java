package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.PrintTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintTaskRepository extends JpaRepository<PrintTask, String> {
    java.util.List<PrintTask> findByUserId(Long userId);
    java.util.List<PrintTask> findByUserIdAndStudentId(Long userId, String studentId);
}
