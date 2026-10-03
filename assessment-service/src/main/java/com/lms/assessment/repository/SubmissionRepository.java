package com.lms.assessment.repository;

import com.lms.assessment.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    List<Submission> findByAssessmentIdAndStudentIdOrderBySubmittedAtDesc(Long assessmentId, Long studentId);
}
