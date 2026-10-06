package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.AiCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    long countByUserIdAndAiTypeAndSuccessTrueAndCreatedAtAfter(Long userId, String aiType, Instant createdAt);

    String FILTER = """
            where (:phone is null or l.phone like %:phone%)
              and (:aiType is null or l.aiType = :aiType)
              and (:start is null or l.createdAt >= :start)
              and (:end is null or l.createdAt <= :end)
            """;

    @Query("select l from AiCallLog l " + FILTER)
    Page<AiCallLog> search(@Param("phone") String phone,
                           @Param("aiType") String aiType,
                           @Param("start") Instant start,
                           @Param("end") Instant end,
                           Pageable pageable);

    @Query("select count(l) from AiCallLog l " + FILTER + " and l.success = true")
    long countSuccess(@Param("phone") String phone,
                      @Param("aiType") String aiType,
                      @Param("start") Instant start,
                      @Param("end") Instant end);

    @Query("select count(l) from AiCallLog l " + FILTER + " and l.success = false")
    long countFailed(@Param("phone") String phone,
                     @Param("aiType") String aiType,
                     @Param("start") Instant start,
                     @Param("end") Instant end);
}
