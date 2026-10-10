package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.BookEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookEntryRepository extends JpaRepository<BookEntry, String> {

    @Query("select e from BookEntry e where e.id=:id and e.userId=:userId and e.studentId is null")
    Optional<BookEntry> findByIdAndUserId(String id, Long userId);

    @Query("select e from BookEntry e where e.id in :ids and e.userId=:userId and e.studentId is null")
    List<BookEntry> findByIdInAndUserId(Collection<String> ids, Long userId);

    @Query("select count(e) from BookEntry e where e.userId=:userId and e.studentId is null")
    long countByUserId(Long userId);

    boolean existsBySubjectId(Long subjectId);

    boolean existsByTopicId(Long topicId);

    long countBySubjectId(Long subjectId);

    long countByTopicId(Long topicId);

    @Query("select distinct e.userId from BookEntry e where e.subjectId is null")
    List<Long> findUserIdsWithUnmigratedEntries();

    @Query("select distinct e.subject from BookEntry e where e.userId = :userId and e.studentId is null and e.subjectId is null")
    List<String> findUnmigratedSubjectNames(@Param("userId") Long userId);

    @Modifying
    @Query("update BookEntry e set e.subjectId = :subjectId where e.userId = :userId and e.studentId is null and e.subjectId is null and e.subject = :name")
    int backfillSubjectId(@Param("userId") Long userId, @Param("name") String name,
                          @Param("subjectId") Long subjectId);

    @Query("select e.subjectId, count(e) from BookEntry e where e.userId = :userId and e.studentId is null and e.subjectId is not null group by e.subjectId")
    List<Object[]> countGroupedBySubjectId(@Param("userId") Long userId);

    String V2_FILTER = """
            where e.userId = :userId and e.studentId is null
              and (:grade is null or e.grade = :grade)
              and (:term is null or e.term = :term)
              and (:subjectId is null or e.subjectId = :subjectId)
              and (:errorType is null or e.errorType = :errorType)
              and (:topicMode = 0 or (:topicMode = 1 and e.topicId = :topicId) or (:topicMode = 2 and e.topicId is null))
              and (:start is null or e.createdAt >= :start)
              and (:end is null or e.createdAt <= :end)
            """;

    @Query("select e from BookEntry e " + V2_FILTER)
    Page<BookEntry> searchV2(@Param("userId") Long userId,
                             @Param("grade") Integer grade,
                             @Param("term") Integer term,
                             @Param("subjectId") Long subjectId,
                             @Param("errorType") String errorType,
                             @Param("topicMode") int topicMode,
                             @Param("topicId") Long topicId,
                             @Param("start") Instant start,
                             @Param("end") Instant end,
                             Pageable pageable);

    @Query("select e from BookEntry e " + V2_FILTER)
    List<BookEntry> listV2(@Param("userId") Long userId,
                           @Param("grade") Integer grade,
                           @Param("term") Integer term,
                           @Param("subjectId") Long subjectId,
                           @Param("errorType") String errorType,
                           @Param("topicMode") int topicMode,
                           @Param("topicId") Long topicId,
                           @Param("start") Instant start,
                           @Param("end") Instant end);

    @Query("select count(e) from BookEntry e where e.userId = :userId and e.studentId is null and e.subjectId in :subjectIds")
    long countByUserIdAndSubjectIdIn(@Param("userId") Long userId, @Param("subjectIds") Collection<Long> subjectIds);

    @Query("""
            select count(e) from BookEntry e
            where e.userId = :userId and e.studentId is null and e.subjectId in :subjectIds
              and (:grade is null or e.grade = :grade)
              and (:term is null or e.term = :term)
              and (:start is null or e.createdAt >= :start)
              and (:end is null or e.createdAt <= :end)
            """)
    long countCustomInScope(@Param("userId") Long userId,
                            @Param("subjectIds") Collection<Long> subjectIds,
                            @Param("grade") Integer grade,
                            @Param("term") Integer term,
                            @Param("start") Instant start,
                            @Param("end") Instant end);

    @Query("select e.topicId, count(e) from BookEntry e where e.userId = :userId and e.studentId is null and e.topicId is not null group by e.topicId")
    List<Object[]> countGroupedByTopicId(@Param("userId") Long userId);

    @Query("""
            select e from BookEntry e
            where e.userId = :userId and e.studentId is null
              and (:grade is null or e.grade = :grade)
              and (:term is null or e.term = :term)
              and (:subject is null or e.subject = :subject)
              and (:errorType is null or e.errorType = :errorType)
            """)
    Page<BookEntry> search(@Param("userId") Long userId,
                           @Param("grade") Integer grade,
                           @Param("term") Integer term,
                           @Param("subject") String subject,
                           @Param("errorType") String errorType,
                           Pageable pageable);

    @Query("""
            select e from BookEntry e
            where e.userId = :userId and e.studentId is null
              and (:grade is null or e.grade = :grade)
              and (:term is null or e.term = :term)
              and (:subject is null or e.subject = :subject)
              and (:start is null or e.createdAt >= :start)
              and (:end is null or e.createdAt <= :end)
            """)
    List<BookEntry> findForAbility(@Param("userId") Long userId,
                                   @Param("grade") Integer grade,
                                   @Param("term") Integer term,
                                   @Param("subject") String subject,
                                   @Param("start") Instant start,
                                   @Param("end") Instant end);

    @Query("""
            select e from BookEntry e
            where e.userId = :userId and e.studentId is null
              and e.errorType = :errorType
              and (:grade is null or e.grade = :grade)
              and (:term is null or e.term = :term)
              and (:subject is null or e.subject = :subject)
            """)
    List<BookEntry> findForRandom(@Param("userId") Long userId,
                                  @Param("errorType") String errorType,
                                  @Param("grade") Integer grade,
                                  @Param("term") Integer term,
                                  @Param("subject") String subject);

    @Modifying
    @Query("update BookEntry e set e.practiceCount = e.practiceCount + 1 where e.id in :ids and e.userId = :userId and e.studentId is null")
    int bumpPracticeCount(@Param("ids") Collection<String> ids, @Param("userId") Long userId);
    java.util.List<BookEntry> findByUserIdAndStudentId(Long userId, String studentId);
}
