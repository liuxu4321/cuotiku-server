package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.PaperDraft;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperDraftRepository extends JpaRepository<PaperDraft, String> {
    java.util.List<PaperDraft> findByUserId(Long userId);
    java.util.List<PaperDraft> findByUserIdAndStudentId(Long userId, String studentId);
}
