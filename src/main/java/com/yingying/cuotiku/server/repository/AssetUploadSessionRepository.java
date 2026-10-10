package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.AssetUploadSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetUploadSessionRepository extends JpaRepository<AssetUploadSession, String> {
    java.util.List<AssetUploadSession> findByUserId(Long userId);
    java.util.List<AssetUploadSession> findByUserIdAndStudentId(Long userId, String studentId);
}
