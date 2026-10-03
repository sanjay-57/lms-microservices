package com.lms.content.service;

import com.lms.content.client.CourseClient;
import com.lms.content.client.CourseInfo;
import com.lms.content.security.AuthUser;
import com.lms.content.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Verifies (through course-service) that the caller may manage the content of a course. */
@Service
@RequiredArgsConstructor
public class CourseAccessService {

    private final CourseClient courseClient;

    public CourseInfo assertCanManage(Long courseId, AuthUser current) {
        if (current.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("Students cannot manage course content");
        }
        CourseInfo course = courseClient.getCourse(courseId, current.getToken());
        if (current.getRole() == Role.INSTRUCTOR && !current.getId().equals(course.instructorId())) {
            throw new AccessDeniedException("You can only manage content of your own courses");
        }
        return course;
    }
}
