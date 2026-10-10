package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    java.util.List<UserProfile> findByUserId(Long userId);
}
