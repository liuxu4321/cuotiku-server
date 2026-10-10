package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.ErrorType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorTypeRepository extends JpaRepository<ErrorType, String> {
    java.util.List<ErrorType> findByUserId(Long userId);
    java.util.List<ErrorType> findByUserIdAndStudentId(Long userId, String studentId);
}
