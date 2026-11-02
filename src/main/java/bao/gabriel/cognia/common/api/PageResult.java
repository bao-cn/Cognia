package bao.gabriel.cognia.common.api;

import java.util.List;

/**
 * Page payload shared by every list endpoint.
 */
public record PageResult<T>(List<T> records, long total, long page, long size, long pages) {

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        long safeSize = size <= 0 ? 1 : size;
        long pages = (total + safeSize - 1) / safeSize;
        return new PageResult<>(records, total, page, safeSize, pages);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return of(List.of(), 0, page, size);
    }
}
