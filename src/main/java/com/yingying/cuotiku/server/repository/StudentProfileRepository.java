package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, String> {
    java.util.List<StudentProfile> findByUserId(Long userId);
}
