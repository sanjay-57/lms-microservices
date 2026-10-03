package com.lms.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lms.assessment.entity.Question;

/** correctOption is only populated for INSTRUCTOR/ADMIN views; students never receive the answers. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionResponse(Long id, Long assessmentId, String questionText,
                               String optionA, String optionB, String optionC, String optionD,
                               String correctOption, Integer marks) {

    public static QuestionResponse from(Question q, boolean includeAnswer) {
        return new QuestionResponse(q.getId(), q.getAssessmentId(), q.getQuestionText(),
                q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(),
                includeAnswer ? q.getCorrectOption() : null, q.getMarks());
    }
}
