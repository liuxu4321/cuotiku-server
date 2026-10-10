package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.QuestionRegion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRegionRepository extends JpaRepository<QuestionRegion, String> {
    java.util.List<QuestionRegion> findByUserId(Long userId);
    java.util.List<QuestionRegion> findByUserIdAndStudentId(Long userId, String studentId);
    java.util.List<QuestionRegion> findByPhotoId(String photoId);
}
