package com.stockvision.watchlist.infrastructure;

import com.stockvision.common.domain.AuditableEntity;
import com.stockvision.watchlist.domain.Watchlist;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA entity mapping the {@code watchlists} table.
 */
@Entity
@Table(name = "watchlists")
public class WatchlistJpaEntity extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    protected WatchlistJpaEntity() {
    }

    public static WatchlistJpaEntity fromDomain(Watchlist watchlist) {
        WatchlistJpaEntity entity = new WatchlistJpaEntity();
        entity.id = watchlist.id();
        entity.userId = watchlist.userId();
        entity.name = watchlist.name();
        entity.description = watchlist.description();
        return entity;
    }

    public void updateMetadata(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Watchlist toDomain() {
        return new Watchlist(id, userId, name, description, getCreatedAt(), getUpdatedAt());
    }
}
