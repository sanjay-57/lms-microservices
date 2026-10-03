package com.lms.course.config;

import com.lms.course.entity.Course;
import com.lms.course.entity.CourseStatus;
import com.lms.course.repository.CourseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds 5 sample courses on first start. instructorId 2 and 3 match the instructors
 * seeded by user-service (instructor@example.com = 2, instructor2@example.com = 3).
 */
@Component
public class CourseDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CourseDataSeeder.class);

    private final CourseRepository courseRepository;

    public CourseDataSeeder(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public void run(String... args) {
        if (courseRepository.count() > 0) {
            return;
        }
        List<Course> courses = List.of(
                course("Java Spring Boot Microservices",
                        "Design and build event-driven microservices with Spring Boot, Eureka, Gateway and RabbitMQ.",
                        "Programming", 2L, 40, "4999.00"),
                course("React Frontend Development",
                        "Modern React with hooks, routing, Axios and state management using Vite.",
                        "Web Development", 2L, 30, "3999.00"),
                course("Python for Data Science",
                        "Python fundamentals, NumPy, Pandas and data visualisation for beginners.",
                        "Data Science", 3L, 45, "4499.00"),
                course("MySQL Database Design",
                        "Relational modelling, normalisation, indexing and query optimisation in MySQL 8.",
                        "Databases", 3L, 20, "2499.00"),
                course("Docker and DevOps Essentials",
                        "Containers, Docker Compose, CI/CD basics and deploying multi-service applications.",
                        "DevOps", 2L, 25, "2999.00")
        );
        courseRepository.saveAll(courses);
        log.info("Seeded {} sample courses", courses.size());
    }

    private Course course(String title, String description, String category, Long instructorId,
                          int hours, String price) {
        return Course.builder().title(title).description(description).category(category)
                .instructorId(instructorId).durationHours(hours).price(new BigDecimal(price))
                .status(CourseStatus.PUBLISHED).build();
    }
}
