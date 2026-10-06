package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    @Query("""
            select u from User u
            where (:keyword is null
                   or u.phone like %:keyword%
                   or coalesce(u.memberNo, '') like %:keyword%)
            """)
    Page<User> search(@Param("keyword") String keyword, Pageable pageable);
}
