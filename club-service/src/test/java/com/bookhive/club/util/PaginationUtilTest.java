package com.bookhive.club.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.bookhive.club.dto.PageResponse;

class PaginationUtilTest {

    private static List<Integer> numbers(int n) {
        return IntStream.rangeClosed(1, n).boxed().toList();
    }

    @Test
    void returnsTheRequestedSlice() {
        PageResponse<Integer> page = PaginationUtil.paginate(numbers(25), 1, 10);

        assertThat(page.content()).containsExactlyElementsOf(IntStream.rangeClosed(11, 20).boxed().toList());
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalElements()).isEqualTo(25);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void lastPageMayBeShort() {
        PageResponse<Integer> page = PaginationUtil.paginate(numbers(25), 2, 10);

        assertThat(page.content()).containsExactly(21, 22, 23, 24, 25);
    }

    @Test
    void pageBeyondTheEndIsEmptyNotAnError() {
        PageResponse<Integer> page = PaginationUtil.paginate(numbers(5), 9, 10);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(5);
    }

    @Test
    void emptyListHasZeroPages() {
        PageResponse<Integer> page = PaginationUtil.paginate(List.of(), 0, 10);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void negativePageAndBadSizesAreCorrected() {
        PageResponse<Integer> negative = PaginationUtil.paginate(numbers(30), -3, 10);
        PageResponse<Integer> zeroSize = PaginationUtil.paginate(numbers(30), 0, 0);
        PageResponse<Integer> hugeSize = PaginationUtil.paginate(numbers(500), 0, 10_000);

        assertThat(negative.page()).isZero();
        assertThat(zeroSize.size()).isEqualTo(PaginationUtil.DEFAULT_SIZE);
        assertThat(hugeSize.size()).isEqualTo(PaginationUtil.MAX_SIZE);
        assertThat(hugeSize.content()).hasSize(PaginationUtil.MAX_SIZE);
    }
}
