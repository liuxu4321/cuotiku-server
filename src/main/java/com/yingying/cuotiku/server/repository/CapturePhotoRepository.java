package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.CapturePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapturePhotoRepository extends JpaRepository<CapturePhoto, String> {
    java.util.List<CapturePhoto> findByUserId(Long userId);
    java.util.List<CapturePhoto> findByUserIdAndStudentId(Long userId, String studentId);
    java.util.List<CapturePhoto> findByBatchId(String batchId);
}
