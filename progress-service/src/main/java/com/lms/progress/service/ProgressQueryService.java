package com.lms.progress.service;

import com.lms.progress.dto.ProgressResponse;
import com.lms.progress.exception.ResourceNotFoundException;
import com.lms.progress.repository.ProgressRepository;
import com.lms.progress.security.AuthUser;
import com.lms.progress.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Query side of CQRS: reads exclusively from the denormalised {@code progress} read model,
 * never from the event store and never modifying anything.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgressQueryService {

    private final ProgressRepository progressRepository;

    public List<ProgressResponse> getByStudent(Long studentId, AuthUser current) {
        checkSelfOrStaff(studentId, current);
        return progressRepository.findByStudentIdOrderByUpdatedAtDesc(studentId)
                .stream().map(ProgressResponse::from).toList();
    }

    public ProgressResponse getByStudentAndCourse(Long studentId, Long courseId, AuthUser current) {
        checkSelfOrStaff(studentId, current);
        return progressRepository.findByStudentIdAndCourseId(studentId, courseId)
                .map(ProgressResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No progress found for student " + studentId + " in course " + courseId));
    }

    public List<ProgressResponse> getByCourse(Long courseId) {
        return progressRepository.findByCourseIdOrderByUpdatedAtDesc(courseId)
                .stream().map(ProgressResponse::from).toList();
    }

    private void checkSelfOrStaff(Long studentId, AuthUser current) {
        if (current.getRole() == Role.STUDENT && !current.getId().equals(studentId)) {
            throw new AccessDeniedException("You can only view your own progress");
        }
    }
}
