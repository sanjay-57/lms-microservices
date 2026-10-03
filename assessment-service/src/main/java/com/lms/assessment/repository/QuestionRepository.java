package com.lms.assessment.repository;

import com.lms.assessment.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByAssessmentIdOrderByIdAsc(Long assessmentId);
}
