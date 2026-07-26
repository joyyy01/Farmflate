package com.example.aiworkspace.controller;

import com.example.aiworkspace.domain.community.CommunityAttachmentEntity;
import com.example.aiworkspace.domain.community.CommunityAttachmentRepository;
import com.example.aiworkspace.dto.community.CommunityAttachmentDto;
import com.example.aiworkspace.security.UserPrincipal;
import com.example.aiworkspace.service.community.AttachmentStorage;
import com.example.aiworkspace.service.community.CommunityAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/community/attachments")
@RequiredArgsConstructor
public class CommunityAttachmentController {

    private final CommunityAttachmentService attachmentService;
    private final CommunityAttachmentRepository attachmentRepository;
    private final AttachmentStorage storage;

    @PostMapping(value = "/images", consumes = "multipart/form-data")
    public ResponseEntity<CommunityAttachmentDto> uploadImage(
            @AuthenticationPrincipal UserPrincipal principal, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.uploadImage(email(principal), file));
    }

    @PostMapping(value = "/files", consumes = "multipart/form-data")
    public ResponseEntity<CommunityAttachmentDto> uploadFile(
            @AuthenticationPrincipal UserPrincipal principal, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.uploadFile(email(principal), file));
    }

    @PostMapping("/links")
    public ResponseEntity<CommunityAttachmentDto> createLink(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.createLink(email(principal), body.get("url")));
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable String attachmentId, @RequestParam(required = false) String v) {
        CommunityAttachmentEntity entity = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부를 찾을 수 없습니다."));
        if (entity.getStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다운로드할 수 없는 첨부입니다.");
        }
        Resource resource = storage.load(entity.getStorageKey());
        MediaType mediaType = entity.getContentType() != null
                ? MediaType.parseMediaType(entity.getContentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitizeHeader(entity.getOriginalName()) + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    private String sanitizeHeader(String name) {
        return name == null ? "attachment" : name.replaceAll("[\\r\\n\"]", "_");
    }

    private String email(UserPrincipal principal) {
        if (principal == null || principal.getEmail() == null || principal.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        }
        return principal.getEmail();
    }
}
