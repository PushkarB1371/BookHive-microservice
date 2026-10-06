package com.bookhive.club.util;

import java.util.List;

import com.bookhive.club.dto.PageResponse;

public final class PaginationUtil {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 50;

    private PaginationUtil() {
    }

    public static int safePage(int page) {
        return Math.max(page, 0);
    }

    public static int safeSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** Slices an in-memory list (used for the cached catalog and the per-user club lists). */
    public static <T> PageResponse<T> paginate(List<T> all, int page, int size) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        int total = all.size();
        int totalPages = (int) Math.ceil((double) total / safeSize);
        int from = (int) Math.min((long) safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        return new PageResponse<>(List.copyOf(all.subList(from, to)), safePage, safeSize, total, totalPages);
    }
}
