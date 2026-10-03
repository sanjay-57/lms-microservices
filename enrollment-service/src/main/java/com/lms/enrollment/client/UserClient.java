package com.lms.enrollment.client;

import com.lms.enrollment.exception.ResourceNotFoundException;
import com.lms.enrollment.exception.ServiceUnavailableException;
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

/** Synchronous call to user-service, forwarding the caller's JWT. */
@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);

    private final RestTemplate restTemplate;

    public UserClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public UserInfo getUser(Long userId, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        try {
            UserInfo user = restTemplate.exchange("http://user-service/api/users/{id}",
                    HttpMethod.GET, new HttpEntity<>(headers), UserInfo.class, userId).getBody();
            if (user == null) {
                throw new ResourceNotFoundException("User not found with id " + userId);
            }
            return user;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("User not found with id " + userId);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
            throw new AccessDeniedException("Not allowed to access user " + userId);
        } catch (RestClientException ex) {
            log.error("user-service call failed for userId={}: {}", userId, ex.getMessage());
            throw new ServiceUnavailableException("User service is currently unavailable");
        }
    }
}
