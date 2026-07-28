package com.farmflate.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface CommunityLikeRepository extends JpaRepository<CommunityLikeEntity, Long> {
    boolean existsByPostIdAndUserEmail(Long postId, String userEmail);

    long countByPostId(Long postId);

    @Modifying
    @Transactional
    void deleteByPostIdAndUserEmail(Long postId, String userEmail);

    List<CommunityLikeEntity> findByUserEmailAndPostIdIn(String userEmail, Collection<Long> postIds);
}
