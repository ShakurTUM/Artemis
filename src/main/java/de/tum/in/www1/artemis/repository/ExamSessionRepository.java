package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.exam.ExamSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ExamSession entity.
 */
@Repository
public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    ExamSession findByStudentExamId(Long studentExamId);

    //    @Query("SELECT examSession FROM ExamSession examSession WHERE examSession.studentExam = :#{#studentExam}")
    //    ExamSession findByStudentExam(@Param("studentExam") StudentExam studentExam);
}