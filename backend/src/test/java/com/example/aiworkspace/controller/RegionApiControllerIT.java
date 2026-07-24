package com.example.aiworkspace.controller;

import com.example.aiworkspace.domain.user.UserRepository;
import com.example.aiworkspace.dto.region.RegionAnalysisStatusDto;
import com.example.aiworkspace.security.CustomOAuth2UserService;
import com.example.aiworkspace.security.JwtTokenProvider;
import com.example.aiworkspace.security.OAuth2SuccessHandler;
import com.example.aiworkspace.security.SecurityConfig;
import com.example.aiworkspace.service.analysis.RegionAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RegionApiController.class)
@Import(SecurityConfig.class)
class RegionApiControllerIT {

    private static final String OWNER_EMAIL = "minsu@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RegionAnalysisService regionAnalysisService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Test
    void creates_at_canonical_path_for_authenticated_owner() throws Exception {
        UUID analysisId = UUID.randomUUID();
        when(regionAnalysisService.create(eq(OWNER_EMAIL), any()))
                .thenReturn(RegionAnalysisStatusDto.builder()
                        .analysisId(analysisId.toString())
                        .status("COMPLETED")
                        .reused(false)
                        .build());

        mockMvc.perform(post("/api/regions/analysis")
                        .with(user(OWNER_EMAIL))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()));

        verify(regionAnalysisService).create(eq(OWNER_EMAIL), any());
    }

    @Test
    void unauthenticated_canonical_create_returns_unauthorized() throws Exception {
        mockMvc.perform(post("/api/regions/analysis")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void report_owned_by_another_user_returns_not_found() throws Exception {
        UUID otherUsersAnalysisId = UUID.randomUUID();
        when(regionAnalysisService.getReport(OWNER_EMAIL, otherUsersAnalysisId))
                .thenThrow(RegionAnalysisService.RegionAnalysisException.analysisNotFound(otherUsersAnalysisId));

        mockMvc.perform(get("/api/regions/reports/{id}", otherUsersAnalysisId)
                        .with(user(OWNER_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGION_ANALYSIS_NOT_FOUND"));

        verify(regionAnalysisService).getReport(OWNER_EMAIL, otherUsersAnalysisId);
    }

    @Test
    void status_owned_by_another_user_returns_not_found() throws Exception {
        UUID otherUsersAnalysisId = UUID.randomUUID();
        when(regionAnalysisService.getStatus(OWNER_EMAIL, otherUsersAnalysisId))
                .thenThrow(RegionAnalysisService.RegionAnalysisException.analysisNotFound(otherUsersAnalysisId));

        mockMvc.perform(get("/api/regions/analysis/{id}/status", otherUsersAnalysisId)
                        .with(user(OWNER_EMAIL)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REGION_ANALYSIS_NOT_FOUND"));

        verify(regionAnalysisService).getStatus(OWNER_EMAIL, otherUsersAnalysisId);
    }

    @Test
    void unmapped_region_returns_domain_error_status() throws Exception {
        when(regionAnalysisService.create(eq(OWNER_EMAIL), any()))
                .thenThrow(RegionAnalysisService.RegionAnalysisException.mappingNotConfigured("52", "52180"));

        mockMvc.perform(post("/api/regions/analysis")
                        .with(user(OWNER_EMAIL))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REGION_MAPPING_NOT_CONFIGURED"));
    }

    private String validRequestJson() {
        return """
                {
                  "sidoCode": "52",
                  "sidoName": "전북특별자치도",
                  "sigunguCode": "52180",
                  "sigunguName": "고창군",
                  "idempotencyKey": "9cf548e3-d37e-4e68-bf79-febd25ae9427",
                  "forceRefresh": false
                }
                """;
    }
}
