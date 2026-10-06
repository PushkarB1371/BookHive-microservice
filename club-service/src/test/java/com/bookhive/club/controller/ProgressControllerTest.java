package com.bookhive.club.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bookhive.club.TestData;
import com.bookhive.club.dto.ProgressResponse;
import com.bookhive.club.dto.UpdateProgressRequest;
import com.bookhive.club.exception.ForbiddenException;
import com.bookhive.club.exception.GlobalExceptionHandler;
import com.bookhive.club.security.AuthenticatedUser;
import com.bookhive.club.service.ProgressService;

@ExtendWith(MockitoExtension.class)
class ProgressControllerTest {

    @Mock
    private ProgressService progressService;

    private MockMvc mockMvc;
    private final AuthenticatedUser ann = TestData.member("Ann");
    private final UUID clubId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProgressController(progressService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        TestData.authenticate(ann);
    }

    @AfterEach
    void tearDown() {
        TestData.clearAuthentication();
    }

    @Test
    void noProgressYetIsNoContent() throws Exception {
        when(progressService.getMyProgress(clubId, ann)).thenReturn(null);

        mockMvc.perform(get("/api/clubs/" + clubId + "/progress/me"))
                .andExpect(status().isNoContent());
    }

    @Test
    void existingProgressIsReturned() throws Exception {
        when(progressService.getMyProgress(clubId, ann)).thenReturn(
                new ProgressResponse(UUID.randomUUID(), ann.id(), "Ann", UUID.randomUUID(), 4, 20, LocalDateTime.now()));

        mockMvc.perform(get("/api/clubs/" + clubId + "/progress/me"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"currentChapter\":4")));
    }

    @Test
    void outOfRangeChapterIsABadRequest() throws Exception {
        when(progressService.updateProgress(any(UUID.class), any(UpdateProgressRequest.class), any(AuthenticatedUser.class)))
                .thenThrow(new IllegalArgumentException("Chapter out of range for this book"));

        mockMvc.perform(put("/api/clubs/" + clubId + "/progress/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentChapter\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("out of range")));
    }

    @Test
    void nonMembersAreForbidden() throws Exception {
        when(progressService.getClubProgress(clubId, ann.id())).thenThrow(new ForbiddenException("Not a member of this club"));

        mockMvc.perform(get("/api/clubs/" + clubId + "/progress"))
                .andExpect(status().isForbidden());
    }
}
