package com.lms.course.repository;

import com.lms.course.entity.Course;
import com.lms.course.entity.CourseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findAllByOrderByCreatedAtDesc();

    List<Course> findByStatusOrderByCreatedAtDesc(CourseStatus status);

    List<Course> findByStatusOrInstructorIdOrderByCreatedAtDesc(CourseStatus status, Long instructorId);

    List<Course> findByInstructorIdOrderByCreatedAtDesc(Long instructorId);

    List<Course> findByInstructorIdAndStatusOrderByCreatedAtDesc(Long instructorId, CourseStatus status);
}
