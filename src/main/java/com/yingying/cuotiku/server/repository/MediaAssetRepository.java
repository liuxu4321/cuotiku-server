package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, String> {
    java.util.List<MediaAsset> findByUserId(Long userId);
    java.util.List<MediaAsset> findByUserIdAndStudentId(Long userId, String studentId);
}
