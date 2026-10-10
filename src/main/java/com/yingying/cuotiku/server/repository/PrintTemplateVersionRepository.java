package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.PrintTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrintTemplateVersionRepository extends JpaRepository<PrintTemplateVersion, String> {
    java.util.List<PrintTemplateVersion> findByTemplateId(String templateId);
}
