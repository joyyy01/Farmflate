package com.example.aiworkspace.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityCommentRepository extends JpaRepository<CommunityCommentEntity, Long> {
    List<CommunityCommentEntity> findByPostIdOrderByCreatedAtAsc(Long postId);
}
