package com.farmflate.domain.farm;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FarmRepository extends JpaRepository<FarmEntity, Long> {
    List<FarmEntity> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    Optional<FarmEntity> findByIdAndUserEmail(Long id, String userEmail);

    List<FarmEntity> findByActiveTrue();
}
