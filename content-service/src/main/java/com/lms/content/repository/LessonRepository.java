package com.lms.content.repository;

import com.lms.content.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByModuleIdOrderByIdAsc(Long moduleId);

    void deleteByModuleId(Long moduleId);
}
