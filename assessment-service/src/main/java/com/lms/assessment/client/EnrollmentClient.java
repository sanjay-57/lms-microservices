package com.lms.assessment.client;

import com.lms.assessment.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/** Synchronous call to enrollment-service, forwarding the caller's JWT. */
@Component
public class EnrollmentClient {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentClient.class);

    private final RestTemplate restTemplate;

    public EnrollmentClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /** True when the student has an ACTIVE enrollment in the course. */
    public boolean isActivelyEnrolled(Long studentId, Long courseId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        try {
            List<EnrollmentInfo> enrollments = restTemplate.exchange(
                    "http://enrollment-service/api/enrollments/student/{id}", HttpMethod.GET,
                    new HttpEntity<>(headers), new ParameterizedTypeReference<List<EnrollmentInfo>>() { },
                    studentId).getBody();
            return enrollments != null && enrollments.stream()
                    .anyMatch(e -> courseId.equals(e.courseId()) && "ACTIVE".equals(e.status()));
        } catch (RestClientException ex) {
            log.error("enrollment-service call failed for studentId={}: {}", studentId, ex.getMessage());
            throw new ServiceUnavailableException("Enrollment service is currently unavailable");
        }
    }
}
