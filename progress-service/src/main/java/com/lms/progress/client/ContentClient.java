package com.lms.progress.client;

import com.lms.progress.security.ServiceTokenProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Synchronous call to content-service (via Eureka) authenticated with a service token, used while
 * initialising progress to learn how many modules a course has.
 */
@Component
@RequiredArgsConstructor
public class ContentClient {

    private static final Logger log = LoggerFactory.getLogger(ContentClient.class);

    private final RestTemplate restTemplate;
    private final ServiceTokenProvider serviceTokenProvider;

    /**
     * @return number of modules of the course (0 if the list is empty)
     * @throws RestClientException when content-service is unreachable or answers with an error
     */
    public int countModules(Long courseId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceTokenProvider.createToken());
        ModuleInfo[] modules = restTemplate.exchange("http://content-service/api/modules/course/{courseId}",
                HttpMethod.GET, new HttpEntity<>(headers), ModuleInfo[].class, courseId).getBody();
        int count = modules == null ? 0 : modules.length;
        log.info("content-service reports {} module(s) for courseId={}", count, courseId);
        return count;
    }
}
