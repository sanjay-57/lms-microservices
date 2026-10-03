package com.lms.assessment.repository;

import com.lms.assessment.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    List<Assessment> findByCourseIdOrderByIdAsc(Long courseId);
}
