package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.SubjectTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubjectTopicRepository extends JpaRepository<SubjectTopic, Long> {

    List<SubjectTopic> findBySubjectIdOrderBySortOrderAscIdAsc(Long subjectId);

    List<SubjectTopic> findByUserIdAndSubjectIdInOrderBySortOrderAscIdAsc(Long userId, Collection<Long> subjectIds);

    Optional<SubjectTopic> findByUserIdAndId(Long userId, Long id);

    Optional<SubjectTopic> findBySubjectIdAndNormalizedName(Long subjectId, String normalizedName);

    long countBySubjectId(Long subjectId);

    boolean existsBySubjectId(Long subjectId);

    @Query("select coalesce(max(t.sortOrder), 0) from SubjectTopic t where t.subjectId = :subjectId")
    int maxSortOrder(@Param("subjectId") Long subjectId);
}
