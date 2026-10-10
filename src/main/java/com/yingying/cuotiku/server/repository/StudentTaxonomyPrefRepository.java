package com.yingying.cuotiku.server.repository;

import com.yingying.cuotiku.server.entity.StudentTaxonomyPref;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentTaxonomyPrefRepository extends JpaRepository<StudentTaxonomyPref, String> {
    java.util.List<StudentTaxonomyPref> findByUserId(Long userId);
    java.util.List<StudentTaxonomyPref> findByUserIdAndStudentId(Long userId, String studentId);
}
