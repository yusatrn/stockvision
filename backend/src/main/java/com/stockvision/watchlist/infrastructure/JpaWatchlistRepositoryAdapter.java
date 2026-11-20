package com.stockvision.watchlist.infrastructure;

import com.stockvision.watchlist.domain.Watchlist;
import com.stockvision.watchlist.domain.WatchlistItem;
import com.stockvision.watchlist.domain.WatchlistRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link WatchlistRepository} to Spring Data JPA.
 */
@Repository
public class JpaWatchlistRepositoryAdapter implements WatchlistRepository {

    private final SpringDataWatchlistRepository watchlistRepository;
    private final SpringDataWatchlistItemRepository itemRepository;

    public JpaWatchlistRepositoryAdapter(
            SpringDataWatchlistRepository watchlistRepository,
            SpringDataWatchlistItemRepository itemRepository) {
        this.watchlistRepository = watchlistRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public List<Watchlist> findAllByUserId(UUID userId) {
        return watchlistRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(WatchlistJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Watchlist> findByIdAndUserId(UUID id, UUID userId) {
        return watchlistRepository.findByIdAndUserId(id, userId).map(WatchlistJpaEntity::toDomain);
    }

    @Override
    public boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name) {
        return watchlistRepository.existsByUserIdAndNameIgnoreCase(userId, name);
    }

    @Override
    public boolean existsByUserIdAndNameIgnoreCaseAndIdNot(
            UUID userId, String name, UUID excludedId) {
        return watchlistRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, excludedId);
    }

    @Override
    public Watchlist save(Watchlist watchlist) {
        WatchlistJpaEntity entity =
                watchlistRepository
                        .findByIdAndUserId(watchlist.id(), watchlist.userId())
                        .map(
                                existing -> {
                                    existing.updateMetadata(
                                            watchlist.name(), watchlist.description());
                                    return existing;
                                })
                        .orElseGet(() -> WatchlistJpaEntity.fromDomain(watchlist));
        return watchlistRepository.saveAndFlush(entity).toDomain();
    }

    @Override
    public void deleteByIdAndUserId(UUID id, UUID userId) {
        itemRepository.deleteAllByWatchlistId(id);
        watchlistRepository.deleteByIdAndUserId(id, userId);
    }

    @Override
    public List<WatchlistItem> findItemsByWatchlistIds(Collection<UUID> watchlistIds) {
        if (watchlistIds == null || watchlistIds.isEmpty()) {
            return List.of();
        }
        return itemRepository.findAllByWatchlistIdInOrderByCreatedAtAsc(watchlistIds).stream()
                .map(WatchlistItemJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<WatchlistItem> findItemByIdAndWatchlistId(UUID itemId, UUID watchlistId) {
        return itemRepository
                .findByIdAndWatchlistId(itemId, watchlistId)
                .map(WatchlistItemJpaEntity::toDomain);
    }

    @Override
    public boolean existsItemByWatchlistIdAndInstrumentId(UUID watchlistId, UUID instrumentId) {
        return itemRepository.existsByWatchlistIdAndInstrumentId(watchlistId, instrumentId);
    }

    @Override
    public WatchlistItem saveItem(WatchlistItem item) {
        return itemRepository.saveAndFlush(WatchlistItemJpaEntity.fromDomain(item)).toDomain();
    }

    @Override
    public void deleteItemByIdAndWatchlistId(UUID itemId, UUID watchlistId) {
        itemRepository.deleteByIdAndWatchlistId(itemId, watchlistId);
    }
}
