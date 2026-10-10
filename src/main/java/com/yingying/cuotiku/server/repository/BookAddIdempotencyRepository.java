package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.BookAddIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface BookAddIdempotencyRepository extends JpaRepository<BookAddIdempotency, Long> {

    Optional<BookAddIdempotency> findByUserIdAndRequestIdAndClientId(Long userId, String requestId, String clientId);

    long deleteByCreatedAtBefore(Instant cutoff);
    java.util.List<BookAddIdempotency> findByUserIdAndStudentId(Long userId, String studentId);
}
