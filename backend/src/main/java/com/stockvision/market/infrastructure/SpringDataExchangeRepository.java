package com.stockvision.market.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ExchangeJpaEntity}.
 */
public interface SpringDataExchangeRepository extends JpaRepository<ExchangeJpaEntity, UUID> {

    List<ExchangeJpaEntity> findAllByActiveTrueOrderByCodeAsc();

    Optional<ExchangeJpaEntity> findByCode(String code);
}
