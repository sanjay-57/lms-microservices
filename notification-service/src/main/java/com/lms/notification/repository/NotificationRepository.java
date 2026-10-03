package com.lms.notification.repository;

import com.lms.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderBySentAtDescIdDesc(Long userId);

    List<Notification> findAllByOrderBySentAtDescIdDesc();

    long countByUserIdAndReadFalse(Long userId);
}
