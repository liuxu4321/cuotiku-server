package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.PrintTaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintTaskEventRepository extends JpaRepository<PrintTaskEvent, String> {
    java.util.List<PrintTaskEvent> findByTaskId(String taskId);
}
