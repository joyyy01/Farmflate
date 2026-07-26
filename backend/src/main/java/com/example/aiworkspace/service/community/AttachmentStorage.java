package com.example.aiworkspace.service.community;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface AttachmentStorage {
    StoredAttachment store(String ownerEmail, MultipartFile file);

    Resource load(String storageKey);

    void delete(String storageKey);

    record StoredAttachment(String storageKey, String originalName, String contentType, long sizeBytes) {
    }
}
