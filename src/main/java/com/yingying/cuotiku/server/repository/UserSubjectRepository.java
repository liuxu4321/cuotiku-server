package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.UserSubject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserSubjectRepository extends JpaRepository<UserSubject, Long> {

    @Query("select s from UserSubject s where s.userId=:userId and s.studentId is null order by s.sortOrder,s.id")
    List<UserSubject> findByUserIdOrderBySortOrderAscIdAsc(Long userId);

    @Query("select s from UserSubject s where s.userId=:userId and s.id=:id and s.studentId is null")
    Optional<UserSubject> findByUserIdAndId(Long userId, Long id);

    @Query("select s from UserSubject s where s.userId=:userId and s.systemKey=:systemKey and s.studentId is null")
    Optional<UserSubject> findByUserIdAndSystemKey(Long userId, String systemKey);

    @Query("select s from UserSubject s where s.userId=:userId and s.normalizedName=:normalizedName and s.studentId is null")
    Optional<UserSubject> findByUserIdAndNormalizedName(Long userId, String normalizedName);

    @Query("select count(s) from UserSubject s where s.userId=:userId and s.studentId is null")
    long countByUserId(Long userId);


    @Query("select s from UserSubject s where s.userId=:userId and s.systemKey is null and s.studentId is null")
    List<UserSubject> findByUserIdAndSystemKeyIsNull(Long userId);

    @Query("select coalesce(max(s.sortOrder), 0) from UserSubject s where s.userId = :userId and s.studentId is null")
    int maxSortOrder(@Param("userId") Long userId);
    java.util.List<UserSubject> findByUserIdAndStudentId(Long userId, String studentId);
}
