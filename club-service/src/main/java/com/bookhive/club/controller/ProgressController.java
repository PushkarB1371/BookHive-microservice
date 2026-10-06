package com.bookhive.club.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookhive.club.dto.ProgressResponse;
import com.bookhive.club.dto.UpdateProgressRequest;
import com.bookhive.club.security.CurrentUser;
import com.bookhive.club.service.ProgressService;

@RestController
@RequestMapping("/api/clubs/{clubId}/progress")
public class ProgressController {

    private final ProgressService progressService;

    public ProgressController(ProgressService progressService) {
        this.progressService = progressService;
    }

    @PutMapping("/me")
    public ProgressResponse updateMyProgress(@PathVariable UUID clubId, @RequestBody UpdateProgressRequest request) {
        return progressService.updateProgress(clubId, request, CurrentUser.get());
    }

    /** 204 No Content when the member hasn't logged anything yet. */
    @GetMapping("/me")
    public ResponseEntity<ProgressResponse> getMyProgress(@PathVariable UUID clubId) {
        ProgressResponse progress = progressService.getMyProgress(clubId, CurrentUser.get());
        return progress == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(progress);
    }

    @GetMapping
    public List<ProgressResponse> getClubProgress(@PathVariable UUID clubId) {
        return progressService.getClubProgress(clubId, CurrentUser.id());
    }
}
