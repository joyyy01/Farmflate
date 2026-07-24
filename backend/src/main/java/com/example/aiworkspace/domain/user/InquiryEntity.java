package com.example.aiworkspace.domain.user;

import com.example.aiworkspace.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userEmail;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String inquiryText;

    @Column(length = 50)
    private String category;

    @Column(nullable = false, length = 20)
    private String status; // PENDING, RESOLVED

    @Builder
    public InquiryEntity(String userEmail, String inquiryText, String category, String status) {
        this.userEmail = userEmail;
        this.inquiryText = inquiryText;
        this.category = category != null ? category : "GENERAL";
        this.status = status != null ? status : "PENDING";
    }
}
