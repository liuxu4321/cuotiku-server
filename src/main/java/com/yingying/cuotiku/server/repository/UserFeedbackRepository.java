package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.UserFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, String> {
    java.util.List<UserFeedback> findByUserId(Long userId);
}
