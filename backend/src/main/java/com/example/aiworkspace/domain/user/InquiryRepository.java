package com.example.aiworkspace.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InquiryRepository extends JpaRepository<InquiryEntity, Long> {
    List<InquiryEntity> findByUserEmailOrderByCreatedAtDesc(String userEmail);
}
