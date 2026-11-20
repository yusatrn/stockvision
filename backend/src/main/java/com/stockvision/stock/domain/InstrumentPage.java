package com.stockvision.stock.domain;

import java.util.List;

/**
 * Paginated slice of instruments returned by repository searches.
 */
public record InstrumentPage(
        List<Instrument> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
