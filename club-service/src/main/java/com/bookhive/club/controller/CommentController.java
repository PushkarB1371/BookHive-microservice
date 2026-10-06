package com.bookhive.club.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookhive.club.dto.CommentResponse;
import com.bookhive.club.dto.CreateCommentRequest;
import com.bookhive.club.dto.PageResponse;
import com.bookhive.club.security.CurrentUser;
import com.bookhive.club.service.CommentService;

@RestController
@RequestMapping("/api/clubs/{clubId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public CommentResponse addComment(@PathVariable UUID clubId, @RequestBody CreateCommentRequest request) {
        return commentService.addComment(clubId, request, CurrentUser.get());
    }

    @GetMapping
    public PageResponse<CommentResponse> getComments(@PathVariable UUID clubId,
                                                     @RequestParam Integer chapter,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        return commentService.getComments(clubId, chapter, page, size, CurrentUser.id());
    }
}
