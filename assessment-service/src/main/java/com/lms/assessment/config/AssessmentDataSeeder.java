package com.lms.assessment.config;

import com.lms.assessment.entity.Assessment;
import com.lms.assessment.entity.Question;
import com.lms.assessment.repository.AssessmentRepository;
import com.lms.assessment.repository.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds one assessment per sample course (course ids 1..5) with 5 questions of 20 marks each
 * (total 100, pass mark 60%).
 */
@Component
public class AssessmentDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AssessmentDataSeeder.class);

    private static final String[] TITLES = {
            "Java Spring Boot Microservices - Final Assessment",
            "React Frontend Development - Final Assessment",
            "Python for Data Science - Final Assessment",
            "MySQL Database Design - Final Assessment",
            "Docker and DevOps Essentials - Final Assessment"
    };

    /** {question, A, B, C, D, correct} per row, five rows per course. */
    private static final String[][][] QUESTIONS = {
            {
                    {"Which annotation marks the entry point of a Spring Boot application?", "@SpringBootApplication", "@EnableWebApp", "@RestApplication", "@BootMain", "A"},
                    {"Which Spring Cloud component provides service discovery?", "Hystrix", "Eureka", "Zuul", "Ribbon Config", "B"},
                    {"What is the default AMQP port of RabbitMQ?", "15672", "8080", "5672", "3306", "C"},
                    {"Which pattern manages distributed transactions using compensating actions?", "Singleton", "Factory", "Observer", "Saga", "D"},
                    {"Which HTTP status code means a resource was created?", "201", "200", "204", "400", "A"}
            },
            {
                    {"Which hook manages local component state?", "useEffect", "useState", "useRef", "useMemo", "B"},
                    {"What does JSX stand for?", "Java Syntax Extension", "JSON Extra", "JavaScript XML", "JavaScript Extension", "C"},
                    {"Which library is commonly used for routing in React apps?", "Redux", "Axios", "Lodash", "React Router", "D"},
                    {"In React, data normally flows from...", "parent to child via props", "child to parent via props", "sibling to sibling directly", "the database to the DOM", "A"},
                    {"Which hook runs side effects after render?", "useState", "useEffect", "useReducer", "useId", "B"}
            },
            {
                    {"Which library provides the DataFrame structure?", "Matplotlib", "Flask", "Pandas", "Django", "C"},
                    {"Which of these is a Python list literal?", "(1, 2, 3)", "{1, 2, 3}", "<1, 2, 3>", "[1, 2, 3]", "D"},
                    {"What is the core array object of NumPy called?", "ndarray", "matrix", "tensor", "vector", "A"},
                    {"Which library is commonly used to plot charts?", "Requests", "Matplotlib", "Pytest", "Celery", "B"},
                    {"Which keyword defines a function in Python?", "func", "function", "def", "lambdaf", "C"}
            },
            {
                    {"Which SQL statement retrieves rows from a table?", "GET", "FETCH TABLE", "OPEN", "SELECT", "D"},
                    {"A primary key must be...", "unique and not null", "text only", "auto-deleted", "repeated", "A"},
                    {"What is used to speed up lookups on a column?", "View", "Index", "Trigger", "Cursor", "B"},
                    {"A JOIN is used to...", "delete tables", "rename columns", "combine rows from two or more tables", "encrypt data", "C"},
                    {"Normalisation mainly aims to...", "increase redundancy", "remove all keys", "slow down queries", "reduce data redundancy", "D"}
            },
            {
                    {"Which file describes how to build a Docker image?", "Dockerfile", "compose.json", "image.txt", "build.xml", "A"},
                    {"Docker Compose files are written in which format?", "XML", "YAML", "INI", "CSV", "B"},
                    {"Which command lists running containers?", "docker images", "docker build", "docker ps", "docker pull", "C"},
                    {"A Docker image is best described as...", "a running process", "a virtual machine disk", "a network switch", "a read-only template for containers", "D"},
                    {"CI in CI/CD stands for...", "Continuous Integration", "Code Inspection", "Container Isolation", "Central Infrastructure", "A"}
            }
    };

    private final AssessmentRepository assessmentRepository;
    private final QuestionRepository questionRepository;

    public AssessmentDataSeeder(AssessmentRepository assessmentRepository, QuestionRepository questionRepository) {
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
    }

    @Override
    public void run(String... args) {
        if (assessmentRepository.count() > 0) {
            return;
        }
        for (int c = 0; c < TITLES.length; c++) {
            Assessment assessment = assessmentRepository.save(Assessment.builder()
                    .courseId(c + 1L)
                    .title(TITLES[c])
                    .passMark(new BigDecimal("60.00"))
                    .totalMarks(BigDecimal.ZERO)
                    .build());

            List<Question> questions = new ArrayList<>();
            for (String[] row : QUESTIONS[c]) {
                questions.add(Question.builder()
                        .assessmentId(assessment.getId())
                        .questionText(row[0])
                        .optionA(row[1]).optionB(row[2]).optionC(row[3]).optionD(row[4])
                        .correctOption(row[5])
                        .marks(20)
                        .build());
            }
            questionRepository.saveAll(questions);
            assessment.setTotalMarks(BigDecimal.valueOf(20L * questions.size()));
            assessmentRepository.save(assessment);
        }
        log.info("Seeded {} assessments with 5 questions each", TITLES.length);
    }
}
