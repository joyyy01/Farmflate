package com.farmflate.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CommunityAttachmentRepository extends JpaRepository<CommunityAttachmentEntity, String> {
    Optional<CommunityAttachmentEntity> findByIdAndOwnerEmail(String id, String ownerEmail);

    List<CommunityAttachmentEntity> findByPostIdOrderBySortOrderAsc(Long postId);

    List<CommunityAttachmentEntity> findByPostIdIsNullAndCreatedAtBefore(LocalDateTime cutoff);
}
