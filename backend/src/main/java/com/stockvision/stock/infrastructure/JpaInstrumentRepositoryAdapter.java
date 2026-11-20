package com.stockvision.stock.infrastructure;

import com.stockvision.stock.domain.Instrument;
import com.stockvision.stock.domain.InstrumentPage;
import com.stockvision.stock.domain.InstrumentRepository;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link InstrumentRepository} to Spring Data JPA.
 */
@Repository
public class JpaInstrumentRepositoryAdapter implements InstrumentRepository {

    private final SpringDataInstrumentRepository springDataInstrumentRepository;

    public JpaInstrumentRepositoryAdapter(
            SpringDataInstrumentRepository springDataInstrumentRepository) {
        this.springDataInstrumentRepository = springDataInstrumentRepository;
    }

    @Override
    public InstrumentPage search(String query, String exchangeCode, int page, int size) {
        String pattern = buildLikePattern(query);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<InstrumentJpaEntity> resultPage;
        if (exchangeCode != null && !exchangeCode.isBlank()) {
            String normalizedExchange = exchangeCode.trim().toUpperCase(Locale.ROOT);
            resultPage =
                    springDataInstrumentRepository.searchActiveByExchange(
                            normalizedExchange, pattern, pageRequest);
        } else {
            resultPage = springDataInstrumentRepository.searchAllActive(pattern, pageRequest);
        }

        List<Instrument> items =
                resultPage.getContent().stream().map(InstrumentJpaEntity::toDomain).toList();
        return new InstrumentPage(
                items,
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages());
    }

    @Override
    public Optional<Instrument> findById(UUID id) {
        return springDataInstrumentRepository
                .findWithExchangeById(id)
                .map(InstrumentJpaEntity::toDomain);
    }

    @Override
    public List<Instrument> findAllByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return springDataInstrumentRepository.findAllWithExchangeByIdIn(ids).stream()
                .map(InstrumentJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Instrument> findBySymbol(String normalizedSymbol) {
        return springDataInstrumentRepository
                .findBySymbolAndActiveTrue(normalizedSymbol)
                .map(InstrumentJpaEntity::toDomain);
    }

    @Override
    public List<Instrument> findActiveByExchangeCode(String exchangeCode) {
        return springDataInstrumentRepository
                .findAllActiveByExchangeOrderedByRank(
                        exchangeCode.trim().toUpperCase(Locale.ROOT))
                .stream()
                .map(InstrumentJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Instrument> findTopActiveByExchangeCode(String exchangeCode, int limit) {
        if (limit < 1) {
            return List.of();
        }
        return springDataInstrumentRepository
                .findAllActiveByExchangeOrderedByRank(
                        exchangeCode.trim().toUpperCase(Locale.ROOT), PageRequest.of(0, limit))
                .stream()
                .map(InstrumentJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countActiveByExchangeCode(String exchangeCode) {
        return springDataInstrumentRepository.countByExchange_CodeAndActiveTrue(
                exchangeCode.trim().toUpperCase(Locale.ROOT));
    }

    private static String buildLikePattern(String query) {
        if (query == null || query.isBlank()) {
            return "%";
        }
        String escaped =
                query.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
