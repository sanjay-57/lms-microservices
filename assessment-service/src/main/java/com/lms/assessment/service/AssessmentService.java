package com.lms.assessment.service;

import com.lms.assessment.client.EnrollmentClient;
import com.lms.assessment.dto.*;
import com.lms.assessment.entity.Assessment;
import com.lms.assessment.entity.Question;
import com.lms.assessment.entity.Submission;
import com.lms.assessment.event.DomainEvent;
import com.lms.assessment.event.EventTypes;
import com.lms.assessment.event.RoutingKeys;
import com.lms.assessment.exception.BadRequestException;
import com.lms.assessment.exception.ResourceNotFoundException;
import com.lms.assessment.messaging.EventPublisher;
import com.lms.assessment.repository.AssessmentRepository;
import com.lms.assessment.repository.QuestionRepository;
import com.lms.assessment.repository.SubmissionRepository;
import com.lms.assessment.security.AuthUser;
import com.lms.assessment.security.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);
    private static final Set<String> OPTIONS = Set.of("A", "B", "C", "D");

    private final AssessmentRepository assessmentRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseAccessService courseAccessService;
    private final EventPublisher eventPublisher;
    private final EnrollmentClient enrollmentClient;

    @Transactional
    public AssessmentResponse create(AssessmentRequest request, AuthUser current) {
        courseAccessService.assertCanManage(request.courseId(), current);
        Assessment saved = assessmentRepository.save(Assessment.builder()
                .courseId(request.courseId())
                .title(request.title().trim())
                .passMark(request.passMark())
                .totalMarks(BigDecimal.ZERO)
                .build());
        log.info("Assessment created: id={}, courseId={}, passMark={}", saved.getId(), saved.getCourseId(), saved.getPassMark());
        return AssessmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> getByCourse(Long courseId) {
        return assessmentRepository.findByCourseIdOrderByIdAsc(courseId).stream().map(AssessmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AssessmentResponse getById(Long id) {
        return AssessmentResponse.from(findOrThrow(id));
    }

    @Transactional
    public QuestionResponse addQuestion(Long assessmentId, QuestionRequest request, AuthUser current) {
        Assessment assessment = findOrThrow(assessmentId);
        courseAccessService.assertCanManage(assessment.getCourseId(), current);

        Question saved = questionRepository.save(Question.builder()
                .assessmentId(assessmentId)
                .questionText(request.questionText().trim())
                .optionA(request.optionA())
                .optionB(request.optionB())
                .optionC(request.optionC())
                .optionD(request.optionD())
                .correctOption(request.correctOption().toUpperCase())
                .marks(request.marks())
                .build());
        assessment.setTotalMarks(assessment.getTotalMarks().add(BigDecimal.valueOf(request.marks())));
        assessmentRepository.save(assessment);
        log.info("Question added: id={}, assessmentId={}, marks={}, totalMarks={}",
                saved.getId(), assessmentId, saved.getMarks(), assessment.getTotalMarks());
        return QuestionResponse.from(saved, true);
    }

    /** Students receive questions WITHOUT the correct option; instructors/admins see the answers. */
    @Transactional(readOnly = true)
    public List<QuestionResponse> getQuestions(Long assessmentId, AuthUser current) {
        Assessment assessment = findOrThrow(assessmentId);
        boolean includeAnswer = current.getRole() != Role.STUDENT;
        if (current.getRole() == Role.INSTRUCTOR) {
            courseAccessService.assertCanManage(assessment.getCourseId(), current);
        }
        return questionRepository.findByAssessmentIdOrderByIdAsc(assessmentId).stream()
                .map(q -> QuestionResponse.from(q, includeAnswer)).toList();
    }

    /**
     * Scores a submission automatically:
     * score = sum of marks of correct answers; percentage = score / totalMarks * 100;
     * passed = percentage >= passMark. Only students with an ACTIVE enrollment may submit. Publishes AssessmentCompleted after the transaction commits.
     */
    @Transactional
    public SubmissionResponse submit(Long assessmentId, SubmitRequest request, AuthUser current) {
        Assessment assessment = findOrThrow(assessmentId);
        List<Question> questions = questionRepository.findByAssessmentIdOrderByIdAsc(assessmentId);
        if (questions.isEmpty()) {
            throw new BadRequestException("This assessment has no questions yet");
        }
        if (!enrollmentClient.isActivelyEnrolled(current.getId(), assessment.getCourseId(), current.getToken())) {
            throw new AccessDeniedException("You must have an active enrollment in course " + assessment.getCourseId());
        }

        Map<Long, Question> byId = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        Map<Long, String> answers = new HashMap<>();
        for (Map.Entry<Long, String> entry : request.answers().entrySet()) {
            if (!byId.containsKey(entry.getKey())) {
                throw new BadRequestException("Question " + entry.getKey() + " does not belong to this assessment");
            }
            String option = entry.getValue() == null ? "" : entry.getValue().trim().toUpperCase();
            if (!OPTIONS.contains(option)) {
                throw new BadRequestException("Answer for question " + entry.getKey() + " must be one of A, B, C, D");
            }
            answers.put(entry.getKey(), option);
        }

        int totalMarks = questions.stream().mapToInt(Question::getMarks).sum();
        int scored = questions.stream()
                .filter(q -> q.getCorrectOption().equalsIgnoreCase(answers.get(q.getId())))
                .mapToInt(Question::getMarks)
                .sum();

        BigDecimal total = BigDecimal.valueOf(totalMarks).setScale(2, RoundingMode.HALF_UP);
        BigDecimal score = BigDecimal.valueOf(scored).setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentage = score.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
        boolean passed = percentage.compareTo(assessment.getPassMark()) >= 0;

        Submission saved = submissionRepository.save(Submission.builder()
                .assessmentId(assessmentId)
                .studentId(current.getId())
                .score(score)
                .percentage(percentage)
                .passed(passed)
                .build());
        log.info("Assessment submitted: submissionId={}, assessmentId={}, studentId={}, score={}/{}, percentage={}, passed={}",
                saved.getId(), assessmentId, current.getId(), score, total, percentage, passed);

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("submissionId", saved.getId());
        payload.put("assessmentId", assessmentId);
        payload.put("courseId", assessment.getCourseId());
        payload.put("studentId", current.getId());
        payload.put("score", score);
        payload.put("totalMarks", total);
        payload.put("percentage", percentage);
        payload.put("passed", passed);
        eventPublisher.publish(RoutingKeys.ASSESSMENT_COMPLETED,
                DomainEvent.of(EventTypes.ASSESSMENT_COMPLETED,
                        DomainEvent.aggregateId(current.getId(), assessment.getCourseId()), payload));

        return SubmissionResponse.from(saved, total);
    }

    private Assessment findOrThrow(Long id) {
        return assessmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found with id " + id));
    }
}
