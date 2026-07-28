package com.farmflate.controller;

import com.farmflate.security.UserPrincipal;
import com.farmflate.service.community.CommunityAttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityAttachmentControllerTest {

    @Test
    void sends_generic_files_as_an_attachment_with_a_safe_content_type() {
        CommunityAttachmentService attachmentService = mock(CommunityAttachmentService.class);
        CommunityAttachmentController controller = new CommunityAttachmentController(attachmentService);
        when(attachmentService.loadForAuthenticatedUser("owner@example.com", "attachment-1"))
                .thenReturn(new CommunityAttachmentService.DownloadAttachment(
                        new ByteArrayResource(new byte[]{1}), "application/octet-stream", "notes.html", false));

        var response = controller.download(new UserPrincipal(1L, "owner@example.com", List.of()), "attachment-1", null);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}
