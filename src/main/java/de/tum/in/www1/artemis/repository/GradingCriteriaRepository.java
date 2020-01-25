package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradingCriteria;

/**
 * Spring Data JPA repository for the GradingCriteria entity.
 */
@Repository
public interface GradingCriteriaRepository extends JpaRepository<GradingCriteria, Long> {
}