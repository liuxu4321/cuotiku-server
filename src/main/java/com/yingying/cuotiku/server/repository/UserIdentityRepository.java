package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, String> {
    java.util.List<UserIdentity> findByUserId(Long userId);
}
