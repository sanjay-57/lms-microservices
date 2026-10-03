package com.lms.content.config;

import com.lms.content.entity.Lesson;
import com.lms.content.entity.Module;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.ModuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds modules and lessons for the five sample courses (course ids 1..5 seeded by course-service).
 * Every course gets 3-5 modules and every module 2-3 lessons.
 */
@Component
public class ContentDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentDataSeeder.class);
    private static final String SAMPLE_VIDEO = "https://www.w3schools.com/html/mov_bbb.mp4";

    private static final String[][] MODULES = {
            // course 1 - Java Spring Boot Microservices
            {"Microservices Fundamentals", "Spring Boot REST APIs", "Service Discovery and API Gateway", "Messaging with RabbitMQ"},
            // course 2 - React Frontend Development
            {"React Basics and JSX", "Hooks and State Management", "Routing and Forms", "API Integration with Axios"},
            // course 3 - Python for Data Science
            {"Python Basics", "NumPy and Pandas", "Data Visualisation"},
            // course 4 - MySQL Database Design
            {"Relational Modelling", "Writing SQL Queries", "Indexes and Query Optimisation"},
            // course 5 - Docker and DevOps Essentials
            {"Container Basics", "Docker Compose", "CI/CD Basics"}
    };

    private final ModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;

    public ContentDataSeeder(ModuleRepository moduleRepository, LessonRepository lessonRepository) {
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
    }

    @Override
    public void run(String... args) {
        if (moduleRepository.count() > 0) {
            return;
        }
        int moduleCount = 0;
        int lessonCount = 0;
        for (int c = 0; c < MODULES.length; c++) {
            long courseId = c + 1L;
            for (int m = 0; m < MODULES[c].length; m++) {
                String moduleTitle = MODULES[c][m];
                Module module = moduleRepository.save(Module.builder()
                        .courseId(courseId)
                        .title(moduleTitle)
                        .description("Module " + (m + 1) + ": " + moduleTitle)
                        .sequenceNumber(m + 1)
                        .build());
                moduleCount++;

                List<Lesson> lessons = new ArrayList<>();
                lessons.add(lesson(module, "Introduction to " + moduleTitle, 10 + m));
                lessons.add(lesson(module, "Hands-on: " + moduleTitle, 20 + m));
                if (m % 2 == 0) {
                    lessons.add(lesson(module, "Summary and Best Practices: " + moduleTitle, 8 + m));
                }
                lessonRepository.saveAll(lessons);
                lessonCount += lessons.size();
            }
        }
        log.info("Seeded {} modules and {} lessons", moduleCount, lessonCount);
    }

    private Lesson lesson(Module module, String title, int minutes) {
        return Lesson.builder()
                .moduleId(module.getId())
                .title(title)
                .description("Learn about: " + title)
                .videoUrl(SAMPLE_VIDEO)
                .documentUrl("https://example.com/docs/course-" + module.getCourseId() + "/module-" + module.getSequenceNumber() + ".pdf")
                .durationMinutes(minutes)
                .build();
    }
}
