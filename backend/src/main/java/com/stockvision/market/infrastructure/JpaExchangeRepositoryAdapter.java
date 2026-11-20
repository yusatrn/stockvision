package com.stockvision.market.infrastructure;

import com.stockvision.market.domain.Exchange;
import com.stockvision.market.domain.ExchangeRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Infrastructure adapter bridging {@link ExchangeRepository} to Spring Data JPA.
 */
@Repository
public class JpaExchangeRepositoryAdapter implements ExchangeRepository {

    private final SpringDataExchangeRepository springDataExchangeRepository;

    public JpaExchangeRepositoryAdapter(SpringDataExchangeRepository springDataExchangeRepository) {
        this.springDataExchangeRepository = springDataExchangeRepository;
    }

    @Override
    public List<Exchange> findAllActive() {
        return springDataExchangeRepository.findAllByActiveTrueOrderByCodeAsc().stream()
                .map(ExchangeJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Exchange> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return springDataExchangeRepository
                .findByCode(code.trim().toUpperCase(Locale.ROOT))
                .map(ExchangeJpaEntity::toDomain);
    }

    @Override
    public Optional<Exchange> findById(UUID id) {
        return springDataExchangeRepository.findById(id).map(ExchangeJpaEntity::toDomain);
    }
}
