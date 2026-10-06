package com.bookhive.club;

import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.bookhive.club.entity.Book;
import com.bookhive.club.entity.Club;
import com.bookhive.club.entity.Membership;
import com.bookhive.club.enums.ClubRole;
import com.bookhive.club.security.AuthenticatedUser;

/** Small builders so the tests read like stories instead of setter lists. */
public final class TestData {

    private TestData() {
    }

    public static AuthenticatedUser admin() {
        return new AuthenticatedUser(UUID.randomUUID(), "Admin", "admin@example.com", "ADMIN");
    }

    public static AuthenticatedUser member(String name) {
        return new AuthenticatedUser(UUID.randomUUID(), name, name.toLowerCase() + "@example.com", "MEMBER");
    }

    public static Book book(String title, int chapters) {
        Book book = new Book();
        book.setId(UUID.randomUUID());
        book.setTitle(title);
        book.setAuthor("Some Author");
        book.setTotalChapters(chapters);
        return book;
    }

    public static Club club(String name, UUID creatorId) {
        Club club = new Club();
        club.setId(UUID.randomUUID());
        club.setName(name);
        club.setCreatedById(creatorId);
        return club;
    }

    public static Membership membership(Club club, UUID userId, ClubRole role) {
        Membership membership = new Membership();
        membership.setId(UUID.randomUUID());
        membership.setClub(club);
        membership.setUserId(userId);
        membership.setRoleInClub(role);
        return membership;
    }

    /** Pretends this user sent the current request (what the JWT filter does in production). */
    public static void authenticate(AuthenticatedUser user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, "jwt-token", List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
