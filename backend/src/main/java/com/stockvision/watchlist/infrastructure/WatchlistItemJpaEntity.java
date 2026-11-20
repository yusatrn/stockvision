package com.stockvision.watchlist.infrastructure;

import com.stockvision.watchlist.domain.WatchlistItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping the {@code watchlist_items} table.
 */
@Entity
@Table(name = "watchlist_items")
public class WatchlistItemJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "watchlist_id", nullable = false, updatable = false)
    private UUID watchlistId;

    @Column(name = "instrument_id", nullable = false, updatable = false)
    private UUID instrumentId;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WatchlistItemJpaEntity() {
    }

    public static WatchlistItemJpaEntity fromDomain(WatchlistItem item) {
        WatchlistItemJpaEntity entity = new WatchlistItemJpaEntity();
        entity.id = item.id();
        entity.watchlistId = item.watchlistId();
        entity.instrumentId = item.instrumentId();
        entity.notes = item.notes();
        entity.createdAt = item.createdAt() != null ? item.createdAt() : Instant.now();
        return entity;
    }

    public WatchlistItem toDomain() {
        return new WatchlistItem(id, watchlistId, instrumentId, notes, createdAt);
    }
}
