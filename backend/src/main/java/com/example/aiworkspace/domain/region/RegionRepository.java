package com.example.aiworkspace.domain.region;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Long> {
    List<Region> findByEnabledTrueOrderBySidoNameAscSigunguNameAsc();
    List<Region> findBySidoCodeAndEnabledTrueOrderBySigunguNameAsc(String sidoCode);
    Optional<Region> findBySidoCodeAndSigunguCode(String sidoCode, String sigunguCode);
}
