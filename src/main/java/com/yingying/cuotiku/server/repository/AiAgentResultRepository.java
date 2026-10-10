package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.AiAgentResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiAgentResultRepository extends JpaRepository<AiAgentResult, Long> {

    Optional<AiAgentResult> findByAgentKeyAndSubjectKeyAndPromptHash(String agentKey, String subjectKey, String promptHash);

    long deleteByAgentKeyAndSubjectKey(String agentKey, String subjectKey);

    long deleteByAgentKey(String agentKey);
    java.util.List<AiAgentResult> findByUserIdAndStudentId(Long userId, String studentId);
}
