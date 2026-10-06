package com.bookhive.club.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookhive.club.dto.ClubResponse;
import com.bookhive.club.dto.CreateClubRequest;
import com.bookhive.club.dto.PageResponse;
import com.bookhive.club.security.CurrentUser;
import com.bookhive.club.service.ClubService;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @PostMapping
    public ClubResponse createClub(@RequestBody CreateClubRequest request) {
        return clubService.createClub(request, CurrentUser.get());
    }

    @GetMapping
    public List<ClubResponse> getAllClubs() {
        return clubService.getAllClubs(CurrentUser.id());
    }

    @GetMapping("/grouped")
    public Map<String, List<ClubResponse>> getGroupedClubs() {
        return clubService.getClubsGroupedForUser(CurrentUser.id());
    }

    @GetMapping("/grouped/paged")
    public PageResponse<ClubResponse> getGroupedPaged(@RequestParam String type,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "6") int size) {
        return clubService.getGroupedPaged(CurrentUser.id(), type, page, size);
    }

    @GetMapping("/reading/{bookId}")
    public List<ClubResponse> getClubsReadingBook(@PathVariable UUID bookId) {
        return clubService.getClubsReadingBook(bookId, CurrentUser.id());
    }

    @GetMapping("/{id}")
    public ClubResponse getClub(@PathVariable UUID id) {
        return clubService.getClubById(id, CurrentUser.id());
    }

    @PostMapping("/{id}/join")
    public Map<String, String> joinClub(@PathVariable UUID id) {
        clubService.joinClub(id, CurrentUser.get());
        return Map.of("message", "Joined club successfully");
    }

    @PutMapping("/{id}/current-book/{bookId}")
    public ClubResponse setCurrentBook(@PathVariable UUID id, @PathVariable UUID bookId) {
        return clubService.setCurrentBook(id, bookId, CurrentUser.get());
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deleteClub(@PathVariable UUID id) {
        clubService.deleteClub(id, CurrentUser.get());
        return Map.of("message", "Club deleted");
    }
}
