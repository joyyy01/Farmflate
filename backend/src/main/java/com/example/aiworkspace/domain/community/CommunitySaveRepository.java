package com.example.aiworkspace.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommunitySaveRepository extends JpaRepository<CommunitySaveEntity, Long> {

    Optional<CommunitySaveEntity> findByUserEmailAndPostId(String userEmail, Long postId);

    boolean existsByUserEmailAndPostId(String userEmail, Long postId);

    List<CommunitySaveEntity> findByUserEmail(String userEmail);

    @Modifying
    @Transactional
    void deleteByUserEmailAndPostId(String userEmail, Long postId);
}
