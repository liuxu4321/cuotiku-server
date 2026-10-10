package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.PaperDraftItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperDraftItemRepository extends JpaRepository<PaperDraftItem, String> {
    java.util.List<PaperDraftItem> findByDraftId(String draftId);
}
