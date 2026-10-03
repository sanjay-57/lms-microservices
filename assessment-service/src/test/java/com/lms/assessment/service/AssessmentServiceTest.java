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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.access.AccessDeniedException;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssessmentServiceTest {

    @Mock private AssessmentRepository assessmentRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private SubmissionRepository submissionRepository;
    @Mock private CourseAccessService courseAccessService;
    @Mock private EventPublisher eventPublisher;
    @Mock private EnrollmentClient enrollmentClient;
    @InjectMocks private AssessmentService assessmentService;

    private final AuthUser student = new AuthUser(4L, "student1@example.com", Role.STUDENT, "token");
    private final AuthUser instructor = new AuthUser(2L, "instructor@example.com", Role.INSTRUCTOR, "token");
    private Assessment assessment;
    private List<Question> questions;

    private Question question(long id, String correct) {
        return Question.builder().id(id).assessmentId(1L).questionText("Q" + id)
                .optionA("a").optionB("b").optionC("c").optionD("d").correctOption(correct).marks(20).build();
    }

    @BeforeEach
    void setUp() {
        assessment = Assessment.builder().id(1L).courseId(10L).title("Final")
                .passMark(new BigDecimal("60.00")).totalMarks(new BigDecimal("100.00")).build();
        questions = List.of(question(1, "A"), question(2, "B"), question(3, "C"), question(4, "D"), question(5, "A"));
        when(assessmentRepository.findById(1L)).thenReturn(Optional.of(assessment));
        when(questionRepository.findByAssessmentIdOrderByIdAsc(1L)).thenReturn(questions);
        when(enrollmentClient.isActivelyEnrolled(4L, 10L, "token")).thenReturn(true);
        when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(99L);
            return s;
        });
    }

    @Test
    void submit_fourOfFiveCorrect_scores80AndPasses() {
        SubmitRequest request = new SubmitRequest(Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "D", 5L, "B"));

        SubmissionResponse response = assessmentService.submit(1L, request, student);

        assertThat(response.score()).isEqualByComparingTo("80");
        assertThat(response.totalMarks()).isEqualByComparingTo("100");
        assertThat(response.percentage()).isEqualByComparingTo("80");
        assertThat(response.passed()).isTrue();

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eq(RoutingKeys.ASSESSMENT_COMPLETED), captor.capture());
        DomainEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo(EventTypes.ASSESSMENT_COMPLETED);
        assertThat(event.aggregateId()).isEqualTo("4-10");
        assertThat(event.longValue("studentId")).isEqualTo(4L);
        assertThat(event.longValue("courseId")).isEqualTo(10L);
        assertThat(event.payload().get("passed")).isEqualTo(true);
    }

    @Test
    void submit_twoOfFiveCorrect_failsButStillPublishesEvent() {
        SubmitRequest request = new SubmitRequest(Map.of(1L, "A", 2L, "B", 3L, "A", 4L, "A", 5L, "B"));

        SubmissionResponse response = assessmentService.submit(1L, request, student);

        assertThat(response.percentage()).isEqualByComparingTo("40");
        assertThat(response.passed()).isFalse();
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publish(eq(RoutingKeys.ASSESSMENT_COMPLETED), captor.capture());
        assertThat(captor.getValue().payload().get("passed")).isEqualTo(false);
    }

    @Test
    void submit_exactlyAtPassMark_passes() {
        // 3 of 5 correct = 60% == passMark 60 -> passed
        SubmitRequest request = new SubmitRequest(Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "A", 5L, "B"));
        SubmissionResponse response = assessmentService.submit(1L, request, student);
        assertThat(response.percentage()).isEqualByComparingTo("60");
        assertThat(response.passed()).isTrue();
    }

    @Test
    void submit_unansweredQuestionsScoreZero_lowercaseAnswersAccepted() {
        SubmitRequest request = new SubmitRequest(Map.of(1L, "a"));
        SubmissionResponse response = assessmentService.submit(1L, request, student);
        assertThat(response.score()).isEqualByComparingTo("20");
        assertThat(response.percentage()).isEqualByComparingTo("20");
        assertThat(response.passed()).isFalse();
    }

    @Test
    void submit_unknownQuestionId_isBadRequest() {
        SubmitRequest request = new SubmitRequest(Map.of(999L, "A"));
        assertThrows(BadRequestException.class, () -> assessmentService.submit(1L, request, student));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void submit_invalidOption_isBadRequest() {
        SubmitRequest request = new SubmitRequest(Map.of(1L, "Z"));
        assertThrows(BadRequestException.class, () -> assessmentService.submit(1L, request, student));
    }

    @Test
    void submit_assessmentWithoutQuestions_isBadRequest() {
        when(questionRepository.findByAssessmentIdOrderByIdAsc(1L)).thenReturn(List.of());
        assertThrows(BadRequestException.class,
                () -> assessmentService.submit(1L, new SubmitRequest(Map.of(1L, "A")), student));
    }

    @Test
    void submit_notEnrolled_isForbidden() {
        when(enrollmentClient.isActivelyEnrolled(4L, 10L, "token")).thenReturn(false);
        assertThrows(AccessDeniedException.class,
                () -> assessmentService.submit(1L, new SubmitRequest(Map.of(1L, "A")), student));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void submit_unknownAssessment_isNotFound() {
        when(assessmentRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> assessmentService.submit(2L, new SubmitRequest(Map.of(1L, "A")), student));
    }

    @Test
    void getQuestions_forStudent_hidesCorrectOption() {
        List<QuestionResponse> result = assessmentService.getQuestions(1L, student);
        assertThat(result).hasSize(5);
        assertThat(result).allSatisfy(q -> assertThat(q.correctOption()).isNull());
    }

    @Test
    void getQuestions_forInstructor_showsCorrectOption() {
        List<QuestionResponse> result = assessmentService.getQuestions(1L, instructor);
        assertThat(result.get(0).correctOption()).isEqualTo("A");
        verify(courseAccessService).assertCanManage(10L, instructor);
    }

    @Test
    void addQuestion_increasesTotalMarks() {
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            q.setId(50L);
            return q;
        });
        assessment.setTotalMarks(BigDecimal.ZERO);

        QuestionResponse response = assessmentService.addQuestion(1L,
                new QuestionRequest("Q?", "a", "b", "c", "d", "b", 25), instructor);

        assertThat(response.correctOption()).isEqualTo("B");
        assertThat(assessment.getTotalMarks()).isEqualByComparingTo("25");
        verify(assessmentRepository).save(assessment);
    }
}
