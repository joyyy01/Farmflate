package com.farmflate.service.community;

import com.farmflate.domain.community.CommunityAttachmentEntity;
import com.farmflate.domain.community.CommunityAttachmentRepository;
import com.farmflate.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommunityAttachmentServiceTest {

    private final CommunityAttachmentRepository attachmentRepository = mock(CommunityAttachmentRepository.class);
    private final AttachmentStorage storage = mock(AttachmentStorage.class);
    private final CommunityAttachmentService service = new CommunityAttachmentService(attachmentRepository, storage);

    @Test
    void blocks_other_users_from_opening_unlinked_attachments() {
        CommunityAttachmentEntity attachment = CommunityAttachmentEntity.builder()
                .id("attachment-1").ownerEmail("owner@example.com").attachmentType("FILE")
                .originalName("notes.txt").contentType("text/plain").storageKey("storage-key").build();
        when(attachmentRepository.findById("attachment-1")).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> service.loadForAuthenticatedUser("other@example.com", "attachment-1"))
                .isInstanceOf(ApiException.class);

        verifyNoInteractions(storage);
    }

    @Test
    void stores_and_serves_generic_files_as_opaque_downloads() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(12L);
        when(file.getOriginalFilename()).thenReturn("notes.txt");
        when(storage.store("owner@example.com", file))
                .thenReturn(new AttachmentStorage.StoredAttachment("storage-key", "notes.txt", "text/html", 12L));
        when(attachmentRepository.save(org.mockito.ArgumentMatchers.any(CommunityAttachmentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.uploadFile("owner@example.com", file);

        ArgumentCaptor<CommunityAttachmentEntity> saved = ArgumentCaptor.forClass(CommunityAttachmentEntity.class);
        verify(attachmentRepository).save(saved.capture());
        assertThat(saved.getValue().getContentType()).isEqualTo("application/octet-stream");

        when(attachmentRepository.findById(saved.getValue().getId())).thenReturn(Optional.of(saved.getValue()));
        when(storage.load("storage-key")).thenReturn(new ByteArrayResource(new byte[]{1}));

        CommunityAttachmentService.DownloadAttachment download =
                service.loadForAuthenticatedUser("owner@example.com", saved.getValue().getId());

        assertThat(download.contentType()).isEqualTo("application/octet-stream");
        assertThat(download.inline()).isFalse();
    }
}
