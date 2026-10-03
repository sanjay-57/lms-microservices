package com.lms.progress.repository;

import com.lms.progress.entity.Progress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProgressRepository extends JpaRepository<Progress, Long> {

    Optional<Progress> findByStudentIdAndCourseId(Long studentId, Long courseId);

    List<Progress> findByStudentIdOrderByUpdatedAtDesc(Long studentId);

    List<Progress> findByCourseIdOrderByUpdatedAtDesc(Long courseId);
}
