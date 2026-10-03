package com.lms.certificate.repository;

import com.lms.certificate.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    List<Certificate> findByStudentIdOrderByIssueDateDesc(Long studentId);

    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    Optional<Certificate> findByVerificationCode(String verificationCode);
}
