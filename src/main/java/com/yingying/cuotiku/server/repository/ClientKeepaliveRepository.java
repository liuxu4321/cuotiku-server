package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.ClientKeepalive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ClientKeepaliveRepository extends JpaRepository<ClientKeepalive, String> {

    @Query("""
            select k from ClientKeepalive k
            where (:onlineOnly = false or k.lastSeenAt >= :onlineSince)
              and (:keyword is null
                   or k.clientId like %:keyword%
                   or coalesce(k.phone, '') like %:keyword%)
            """)
    Page<ClientKeepalive> search(@Param("onlineOnly") boolean onlineOnly,
                                 @Param("onlineSince") Instant onlineSince,
                                 @Param("keyword") String keyword,
                                 Pageable pageable);
}
