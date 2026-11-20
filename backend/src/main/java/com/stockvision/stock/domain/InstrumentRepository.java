package com.stockvision.stock.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for financial instruments.
 */
public interface InstrumentRepository {

    InstrumentPage search(String query, String exchangeCode, int page, int size);

    Optional<Instrument> findById(UUID id);

    List<Instrument> findAllByIds(Collection<UUID> ids);

    Optional<Instrument> findBySymbol(String normalizedSymbol);

    List<Instrument> findActiveByExchangeCode(String exchangeCode);

    List<Instrument> findTopActiveByExchangeCode(String exchangeCode, int limit);

    long countActiveByExchangeCode(String exchangeCode);
}
