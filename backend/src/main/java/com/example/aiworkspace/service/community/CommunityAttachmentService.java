package com.example.aiworkspace.service.community;

import com.example.aiworkspace.domain.community.CommunityAttachmentEntity;
import com.example.aiworkspace.domain.community.CommunityAttachmentRepository;
import com.example.aiworkspace.dto.community.CommunityAttachmentDto;
import com.example.aiworkspace.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Validates and persists uploaded images/files and pasted links as typed
 * attachments. Nothing here trusts the client's declared content-type: image
 * bytes are magic-number sniffed before being accepted, and links are
 * restricted to HTTPS with obviously-unsafe hosts rejected outright.
 */
@Service
@RequiredArgsConstructor
public class CommunityAttachmentService {

    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_MIME = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> BLOCKED_FILE_EXTENSIONS = Set.of("svg", "html", "htm", "js", "exe", "sh", "bat");

    private final CommunityAttachmentRepository attachmentRepository;
    private final AttachmentStorage storage;

    @Transactional
    public CommunityAttachmentDto uploadImage(String ownerEmail, MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("이미지 파일이 비어 있습니다.");
        if (file.getSize() > MAX_IMAGE_BYTES) throw tooLarge("이미지는 8MB를 넘을 수 없습니다.");

        String sniffed = sniffImageType(file);
        if (sniffed == null) {
            if (isLikelyHeic(file)) {
                throw invalid("HEIC 이미지는 지원하지 않습니다. JPEG/PNG/WEBP로 변환한 뒤 업로드해 주세요.");
            }
            throw invalid("지원하지 않는 이미지 형식입니다. JPEG, PNG, WEBP만 업로드할 수 있습니다.");
        }

        AttachmentStorage.StoredAttachment stored = storage.store(ownerEmail, file);
        CommunityAttachmentEntity entity = attachmentRepository.save(CommunityAttachmentEntity.builder()
                .id(UUID.randomUUID().toString()).ownerEmail(ownerEmail).attachmentType("IMAGE")
                .originalName(stored.originalName()).contentType(sniffed).sizeBytes(stored.sizeBytes())
                .storageKey(stored.storageKey()).sortOrder(0).build());
        return toDto(entity);
    }

    @Transactional
    public CommunityAttachmentDto uploadFile(String ownerEmail, MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("첨부 파일이 비어 있습니다.");
        if (file.getSize() > MAX_FILE_BYTES) throw tooLarge("파일은 10MB를 넘을 수 없습니다.");

        String extension = extensionOf(file.getOriginalFilename());
        if (BLOCKED_FILE_EXTENSIONS.contains(extension)) {
            throw invalid("허용되지 않는 파일 형식입니다.");
        }

        AttachmentStorage.StoredAttachment stored = storage.store(ownerEmail, file);
        CommunityAttachmentEntity entity = attachmentRepository.save(CommunityAttachmentEntity.builder()
                .id(UUID.randomUUID().toString()).ownerEmail(ownerEmail).attachmentType("FILE")
                .originalName(stored.originalName()).contentType(stored.contentType()).sizeBytes(stored.sizeBytes())
                .storageKey(stored.storageKey()).sortOrder(0).build());
        return toDto(entity);
    }

    @Transactional
    public CommunityAttachmentDto createLink(String ownerEmail, String url) {
        validateLink(url);
        CommunityAttachmentEntity entity = attachmentRepository.save(CommunityAttachmentEntity.builder()
                .id(UUID.randomUUID().toString()).ownerEmail(ownerEmail).attachmentType("LINK")
                .originalName(url).sizeBytes(0).externalUrl(url).sortOrder(0).build());
        return toDto(entity);
    }

    /** Attaches only the caller's own unlinked attachments to a newly created post, in the given order. */
    @Transactional
    public List<CommunityAttachmentEntity> linkToPost(String ownerEmail, Long postId, List<String> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) return List.of();
        if (attachmentIds.size() > 5) throw invalid("첨부는 최대 5개까지 가능합니다.");

        List<CommunityAttachmentEntity> linked = new java.util.ArrayList<>();
        int order = 0;
        for (String attachmentId : attachmentIds) {
            CommunityAttachmentEntity entity = attachmentRepository.findByIdAndOwnerEmail(attachmentId, ownerEmail)
                    .orElseThrow(() -> ApiException.notFound("COMMUNITY_ATTACHMENT_INVALID", "첨부를 찾을 수 없습니다: " + attachmentId));
            if (entity.getPostId() != null) {
                throw invalid("이미 다른 게시글에 사용된 첨부입니다: " + attachmentId);
            }
            entity.setPostId(postId);
            entity.setSortOrder(order++);
            linked.add(attachmentRepository.save(entity));
        }
        return linked;
    }

    @Transactional(readOnly = true)
    public List<CommunityAttachmentEntity> findForPost(Long postId) {
        return attachmentRepository.findByPostIdOrderBySortOrderAsc(postId);
    }

    /** Best-effort cleanup of attachments a client uploaded but never attached to a post. */
    @Transactional
    public void cleanupStaleUnlinkedAttachments() {
        List<CommunityAttachmentEntity> stale = attachmentRepository
                .findByPostIdIsNullAndCreatedAtBefore(LocalDateTime.now().minusMinutes(30));
        for (CommunityAttachmentEntity entity : stale) {
            if (entity.getStorageKey() != null) storage.delete(entity.getStorageKey());
            attachmentRepository.delete(entity);
        }
    }

    private void validateLink(String url) {
        if (url == null || url.isBlank()) throw invalid("링크 URL이 비어 있습니다.");
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("javascript:") || lower.startsWith("data:")) {
            throw invalid("허용되지 않는 링크 형식입니다.");
        }
        if (!lower.startsWith("https://")) {
            throw invalid("링크는 https 주소만 허용됩니다.");
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (Exception exception) {
            throw invalid("링크 형식이 올바르지 않습니다.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (host.isEmpty() || host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")
                || host.startsWith("10.") || host.startsWith("192.168.")
                || host.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*")
                || host.startsWith("169.254.")) {
            throw invalid("사설망 또는 로컬 주소로의 링크는 허용되지 않습니다.");
        }
        if (trimmed.length() > 2000) throw invalid("링크가 너무 깁니다.");
    }

    private String sniffImageType(MultipartFile file) {
        byte[] header = readHeader(file, 16);
        if (header == null) return null;
        if (header.length >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (header.length >= 4 && (header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
            return "image/png";
        }
        if (header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private boolean isLikelyHeic(MultipartFile file) {
        byte[] header = readHeader(file, 32);
        if (header == null || header.length < 12) return false;
        String box = new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
        if (!"ftyp".equals(box)) return false;
        String brand = new String(header, 8, Math.min(4, header.length - 8), java.nio.charset.StandardCharsets.US_ASCII);
        return brand.startsWith("heic") || brand.startsWith("heix") || brand.startsWith("hevc") || brand.startsWith("mif1");
    }

    private byte[] readHeader(MultipartFile file, int length) {
        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[length];
            int read = input.read(buffer);
            if (read <= 0) return null;
            return read == length ? buffer : java.util.Arrays.copyOf(buffer, read);
        } catch (IOException exception) {
            return null;
        }
    }

    private String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    public CommunityAttachmentDto toDto(CommunityAttachmentEntity entity) {
        return CommunityAttachmentDto.builder()
                .id(entity.getId())
                .type(entity.getAttachmentType())
                .name(entity.getOriginalName())
                .contentType(entity.getContentType())
                .sizeBytes(entity.getSizeBytes())
                .url("LINK".equals(entity.getAttachmentType()) ? entity.getExternalUrl()
                        : "/api/community/attachments/" + entity.getId())
                .order(entity.getSortOrder())
                .build();
    }

    private ApiException invalid(String message) {
        return ApiException.badRequest("COMMUNITY_ATTACHMENT_INVALID", message);
    }

    private ApiException tooLarge(String message) {
        return ApiException.tooLarge("COMMUNITY_ATTACHMENT_TOO_LARGE", message);
    }
}
