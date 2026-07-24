package com.example.aiworkspace.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommunityPostRepository extends JpaRepository<CommunityPostEntity, Long> {
    List<CommunityPostEntity> findAllByOrderByCreatedAtDesc();
    List<CommunityPostEntity> findByCategoryOrderByCreatedAtDesc(String category);
}
