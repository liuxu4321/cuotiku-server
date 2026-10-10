package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.ImageRevision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRevisionRepository extends JpaRepository<ImageRevision, String> {
    java.util.List<ImageRevision> findByUserId(Long userId);
    java.util.List<ImageRevision> findByUserIdAndStudentId(Long userId, String studentId);
    java.util.List<ImageRevision> findByPhotoId(String photoId);
}
