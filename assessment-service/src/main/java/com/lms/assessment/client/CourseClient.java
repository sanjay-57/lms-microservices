package com.lms.assessment.client;

import com.lms.assessment.exception.ResourceNotFoundException;
import com.lms.assessment.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Synchronous call to course-service, forwarding the caller's JWT. */
@Component
public class CourseClient {

    private static final Logger log = LoggerFactory.getLogger(CourseClient.class);

    private final RestTemplate restTemplate;

    public CourseClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public CourseInfo getCourse(Long courseId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        try {
            CourseInfo course = restTemplate.exchange("http://course-service/api/courses/{id}",
                    HttpMethod.GET, new HttpEntity<>(headers), CourseInfo.class, courseId).getBody();
            if (course == null) {
                throw new ResourceNotFoundException("Course not found with id " + courseId);
            }
            return course;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Course not found or not available: " + courseId);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            throw new AccessDeniedException("Not allowed to access course " + courseId);
        } catch (RestClientException ex) {
            log.error("course-service call failed for courseId={}: {}", courseId, ex.getMessage());
            throw new ServiceUnavailableException("Course service is currently unavailable");
        }
    }
}
