package com.stockvision.market.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for exchanges.
 */
public interface ExchangeRepository {

    List<Exchange> findAllActive();

    Optional<Exchange> findByCode(String code);

    Optional<Exchange> findById(UUID id);
}
