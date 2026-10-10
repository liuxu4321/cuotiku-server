package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.ProcessingJobItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingJobItemRepository extends JpaRepository<ProcessingJobItem, String> {
    java.util.List<ProcessingJobItem> findByPhotoId(String photoId);
    java.util.List<ProcessingJobItem> findByJobId(String jobId);
}
