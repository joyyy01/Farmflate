package com.example.aiworkspace.domain.farm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FieldActivityLogRepository extends JpaRepository<FieldActivityLogEntity, Long> {
    Optional<FieldActivityLogEntity> findByOwnerEmailAndIdempotencyKey(String ownerEmail, String idempotencyKey);

    List<FieldActivityLogEntity> findByFarmIdAndOwnerEmailAndLoggedAtBetweenOrderByLoggedAtDesc(
            Long farmId, String ownerEmail, LocalDateTime from, LocalDateTime to);
}
