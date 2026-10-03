package com.lms.content.repository;

import com.lms.content.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModuleRepository extends JpaRepository<Module, Long> {

    List<Module> findByCourseIdOrderBySequenceNumberAsc(Long courseId);

    boolean existsByCourseIdAndSequenceNumber(Long courseId, Integer sequenceNumber);
}
