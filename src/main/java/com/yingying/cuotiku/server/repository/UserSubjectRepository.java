package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.UserSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserSubjectRepository extends JpaRepository<UserSubject, Long> {

    List<UserSubject> findByUserIdOrderBySortOrderAscIdAsc(Long userId);

    Optional<UserSubject> findByUserIdAndId(Long userId, Long id);

    Optional<UserSubject> findByUserIdAndSystemKey(Long userId, String systemKey);

    Optional<UserSubject> findByUserIdAndNormalizedName(Long userId, String normalizedName);

    long countByUserId(Long userId);


    List<UserSubject> findByUserIdAndSystemKeyIsNull(Long userId);

    @Query("select coalesce(max(s.sortOrder), 0) from UserSubject s where s.userId = :userId")
    int maxSortOrder(@Param("userId") Long userId);
}
