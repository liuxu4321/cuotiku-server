package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.BookPracticeRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface BookPracticeRecordRepository extends JpaRepository<BookPracticeRecord, Long> {

    interface EntryStat {
        String getEntryId();
        long getTotal();
        long getCorrect();
        Instant getLastAt();
    }

    @Query("""
            select p.entryId as entryId, count(p) as total,
                   sum(case when p.correct then 1 else 0 end) as correct,
                   max(p.practicedAt) as lastAt
            from BookPracticeRecord p
            where p.entryId in :ids
            group by p.entryId
            """)
    List<EntryStat> statsByEntries(@Param("ids") Collection<String> ids);

    Page<BookPracticeRecord> findByEntryIdAndUserId(String entryId, Long userId, Pageable pageable);

    @Query("""
            select p from BookPracticeRecord p
            where p.userId = :userId
              and (:correct is null or p.correct = :correct)
              and (:start is null or p.practicedAt >= :start)
              and (:end is null or p.practicedAt <= :end)
            """)
    Page<BookPracticeRecord> searchHistory(@Param("userId") Long userId,
                                           @Param("correct") Boolean correct,
                                           @Param("start") Instant start,
                                           @Param("end") Instant end,
                                           Pageable pageable);

    @Modifying
    @Query("delete from BookPracticeRecord p where p.entryId = :entryId")
    int deleteByEntryId(@Param("entryId") String entryId);

    long countByUserId(Long userId);

    long countByUserIdAndCorrectTrue(Long userId);

    @Query("select max(p.practicedAt) from BookPracticeRecord p where p.userId = :userId")
    Instant lastPracticedAt(@Param("userId") Long userId);
}
