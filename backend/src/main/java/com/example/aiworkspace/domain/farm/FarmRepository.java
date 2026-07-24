package com.example.aiworkspace.domain.farm;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FarmRepository extends JpaRepository<FarmEntity, Long> {
    List<FarmEntity> findByUserEmailOrderByCreatedAtDesc(String userEmail);
}
