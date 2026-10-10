package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.PrintTaskItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintTaskItemRepository extends JpaRepository<PrintTaskItem, String> {
    java.util.List<PrintTaskItem> findByTaskId(String taskId);
}
