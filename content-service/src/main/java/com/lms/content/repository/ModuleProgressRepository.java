package com.lms.content.repository;

import com.lms.content.entity.ModuleProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModuleProgressRepository extends JpaRepository<ModuleProgress, Long> {

    Optional<ModuleProgress> findByStudentIdAndModuleId(Long studentId, Long moduleId);

    List<ModuleProgress> findByStudentIdAndCourseIdAndCompletedTrue(Long studentId, Long courseId);

    long countByStudentIdAndCourseIdAndCompletedTrue(Long studentId, Long courseId);

    void deleteByModuleId(Long moduleId);
}
