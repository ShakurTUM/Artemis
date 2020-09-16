package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.in.www1.artemis.domain.enumeration.CategoryState;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.StaticCodeAnalysisResource;

class StaticCodeAnalysisIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    private StaticCodeAnalysisService staticCodeAnalysisService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private ProgrammingExercise programmingExerciseSCAEnabled;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() {
        database.addUsers(1, 1, 1);
        programmingExerciseSCAEnabled = database.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();
        var tempProgrammingEx = ModelFactory.generateProgrammingExercise(ZonedDateTime.now(), ZonedDateTime.now().plusDays(1),
                programmingExerciseSCAEnabled.getCourseViaExerciseGroupOrCourseMember());
        programmingExercise = programmingExerciseRepository.save(tempProgrammingEx);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    private String parameterizeEndpoint(String endpoint, ProgrammingExercise exercise) {
        return endpoint.replace("{exerciseId}", String.valueOf(exercise.getId()));
    }

    @Test
    void testCreateDefaultCategories_noConfigurationAvailable() {
        // Haskell does not have a default configuration at the time of creation of this test
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.HASKELL);
        staticCodeAnalysisService.createDefaultCategories(programmingExercise);
        var categories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExercise.getId());
        assertThat(categories).isEmpty();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testGetStaticCodeAnalysisCategories() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        var categories = request.get(endpoint, HttpStatus.OK, new TypeReference<Set<StaticCodeAnalysisCategory>>() {
        });
        assertThat(programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories()).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(categories);
    }

    @Test
    @WithMockUser(value = "student1", roles = "STUDENT")
    void testGetStaticCodeAnalysisCategories_asStudent_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        request.getList(endpoint, HttpStatus.FORBIDDEN, StaticCodeAnalysisCategory.class);
    }

    @Test
    @WithMockUser(username = "other-ta1", roles = "TA")
    void testGetStaticCodeAnalysisCategories_notAtLeastTAInCourse_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        database.addTeachingAssistant("other-tas", "other-ta");
        request.getList(endpoint, HttpStatus.FORBIDDEN, StaticCodeAnalysisCategory.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testGetStaticCodeAnalysisCategories_staticCodeAnalysisNotEnabled_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExercise);
        request.getList(endpoint, HttpStatus.BAD_REQUEST, StaticCodeAnalysisCategory.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        // Change the first category
        var categoryIterator = programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator();
        var firstCategory = categoryIterator.next();
        firstCategory.setState(CategoryState.GRADED);
        firstCategory.setPenalty(33);
        firstCategory.setMaxPenalty(44);
        // Remove the second category
        var removedCategory = categoryIterator.next();
        categoryIterator.remove();

        var responseCategories = request.patchWithResponseBody(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(),
                new TypeReference<List<StaticCodeAnalysisCategory>>() {
                }, HttpStatus.OK);
        var savedCategories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExerciseSCAEnabled.getId());

        // The removed category should not be deleted
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().add(removedCategory);
        assertThat(responseCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(savedCategories);
        assertThat(responseCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories());
        assertThat(savedCategories).usingRecursiveFieldByFieldElementComparator().usingElementComparatorIgnoringFields("exercise")
                .containsExactlyInAnyOrderElementsOf(programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories());
    }

    @Test
    @WithMockUser(value = "student1", roles = "STUDENT")
    void testUpdateStaticCodeAnalysisCategories_asStudent_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "other-ta1", roles = "TA")
    void testUpdateStaticCodeAnalysisCategories_notAtLeastTAInCourse_forbidden() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        database.addTeachingAssistant("other-tas", "other-ta");
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_staticCodeAnalysisNotEnabled_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExercise);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_categoryIdMissing_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setId(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_penaltyNullOrNegative_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setPenalty(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setPenalty(-1);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_maxPenaltySmallerThanPenalty_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        var category = programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next();
        category.setMaxPenalty(3);
        category.setPenalty(5);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_stateIsNull_badRequest() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().setState(null);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void testUpdateStaticCodeAnalysisCategories_exerciseIdsDoNotMatch_conflict() throws Exception {
        var endpoint = parameterizeEndpoint("/api" + StaticCodeAnalysisResource.Endpoints.CATEGORIES, programmingExerciseSCAEnabled);
        programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories().iterator().next().getExercise().setId(1234L);
        request.patch(endpoint, programmingExerciseSCAEnabled.getStaticCodeAnalysisCategories(), HttpStatus.CONFLICT);
    }

    @Test
    void testDeletionOfStaticCodeAnalysisCategoriesOnExerciseDeletion() {
        programmingExerciseRepository.delete(programmingExerciseSCAEnabled);
        var categories = staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExerciseSCAEnabled.getId());
        assertThat(categories).isEmpty();
    }
}
