package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.StudentParticipation;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ModelingAssessmentService extends AssessmentService {

    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentService.class);

    private final ModelingSubmissionService modelingSubmissionService;

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    private final CompassService compassService;

    public ModelingAssessmentService(UserService userService, ComplaintResponseService complaintResponseService, CompassService compassService,
            ModelingSubmissionRepository modelingSubmissionRepository, ComplaintRepository complaintRepository, ResultRepository resultRepository,
            StudentParticipationRepository studentParticipationRepository, ResultService resultService, AuthorizationCheckService authCheckService,
            ModelingSubmissionService modelingSubmissionService) {
        super(complaintResponseService, complaintRepository, resultRepository, studentParticipationRepository, resultService, authCheckService, userService);
        this.compassService = compassService;
        this.modelingSubmissionRepository = modelingSubmissionRepository;
        this.modelingSubmissionService = modelingSubmissionService;
    }

    /**
     * This function is used for submitting a manual assessment/result. It gets the result that belongs to the given resultId, updates the completion date, sets the assessment type
     * to MANUAL and sets the assessor attribute. Afterwards, it saves the update result in the database again.
     *
     * @param resultId the id of the result that should be submitted
     * @param exercise the exercise the assessment belongs to
     * @param submissionDate the date manual assessment was submitted
     * @return the ResponseEntity with result as body
     */
    @Transactional
    public Result submitManualAssessment(long resultId, ModelingExercise exercise, ZonedDateTime submissionDate) {
        // TODO CZ: use AssessmentService#submitResult() function instead
        Result result = resultRepository.findWithEagerSubmissionAndFeedbackAndAssessorById(resultId)
                .orElseThrow(() -> new EntityNotFoundException("No result for the given resultId could be found"));
        result.setRatedIfNotExceeded(exercise.getDueDate(), submissionDate);
        result.setCompletionDate(ZonedDateTime.now());
        result.evaluateFeedback(exercise.getMaxScore()); // TODO CZ: move to AssessmentService class, as it's the same for modeling and text exercises (i.e. total score is sum of
        // feedback credits)
        return resultRepository.save(result);
    }

    /**
     * This function is used for saving a manual assessment/result. It sets the assessment type to MANUAL and sets the assessor attribute. Furthermore, it saves the result in the
     * database.
     *
     * @param modelingSubmission the modeling submission to which the feedback belongs to
     * @param modelingAssessment the assessment as a feedback list that should be added to the result of the corresponding submission
     * @param modelingExercise the modeling exercise for which assessment due date is checked
     * @return result that was saved in the database
     */
    @Transactional
    public Result saveManualAssessment(ModelingSubmission modelingSubmission, List<Feedback> modelingAssessment, ModelingExercise modelingExercise) {
        Result result = modelingSubmission.getResult();
        if (result == null) {
            result = modelingSubmissionService.setNewResult(modelingSubmission);
        }
        checkGeneralFeedback(modelingAssessment);

        return saveAssessment(result, modelingAssessment, modelingSubmission, modelingExercise, modelingSubmissionRepository);
    }

    /**
     * Cancel an assessment of a given modeling submission for the current user, i.e. delete the corresponding result / release the lock and tell Compass that the submission is
     * available for assessment again.
     *
     * @param modelingSubmission the modeling submission for which the current assessment should be canceled
     */
    public void cancelAssessmentOfSubmission(ModelingSubmission modelingSubmission) {
        super.cancelAssessmentOfSubmission(modelingSubmission);
        var studentParticipation = (StudentParticipation) modelingSubmission.getParticipation();
        ModelingExercise modelingExercise = (ModelingExercise) studentParticipation.getExercise();
        compassService.cancelAssessmentForSubmission(modelingExercise, modelingSubmission.getId());
    }

    /**
     * Gets an example modeling submission with the given submissionId and returns the result of the submission.
     *
     * @param submissionId the id of the example modeling submission
     * @return the result of the submission
     * @throws EntityNotFoundException when no submission can be found for the given id
     */
    @Transactional(readOnly = true)
    public Result getExampleAssessment(long submissionId) {
        Optional<ModelingSubmission> optionalModelingSubmission = modelingSubmissionRepository.findExampleSubmissionByIdWithEagerResult(submissionId);
        ModelingSubmission modelingSubmission = optionalModelingSubmission
                .orElseThrow(() -> new EntityNotFoundException("Example Submission with id \"" + submissionId + "\" does not exist"));
        return modelingSubmission.getResult();
    }
}
