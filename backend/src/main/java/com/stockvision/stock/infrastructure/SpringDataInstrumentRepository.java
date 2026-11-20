package com.stockvision.stock.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link InstrumentJpaEntity}.
 */
public interface SpringDataInstrumentRepository extends JpaRepository<InstrumentJpaEntity, UUID> {

    @EntityGraph(attributePaths = "exchange")
    Optional<InstrumentJpaEntity> findWithExchangeById(UUID id);

    @EntityGraph(attributePaths = "exchange")
    List<InstrumentJpaEntity> findAllWithExchangeByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = "exchange")
    Optional<InstrumentJpaEntity> findBySymbolAndActiveTrue(String symbol);

    @EntityGraph(attributePaths = "exchange")
    @Query(
            """
            SELECT i FROM InstrumentJpaEntity i
            WHERE i.active = true
              AND UPPER(i.exchange.code) = :exchangeCode
            ORDER BY COALESCE(i.catalogRank, 999999) ASC, i.symbol ASC
            """)
    List<InstrumentJpaEntity> findAllActiveByExchangeOrderedByRank(
            @Param("exchangeCode") String exchangeCode);

    @EntityGraph(attributePaths = "exchange")
    @Query(
            """
            SELECT i FROM InstrumentJpaEntity i
            WHERE i.active = true
              AND UPPER(i.exchange.code) = :exchangeCode
            ORDER BY COALESCE(i.catalogRank, 999999) ASC, i.symbol ASC
            """)
    List<InstrumentJpaEntity> findAllActiveByExchangeOrderedByRank(
            @Param("exchangeCode") String exchangeCode, Pageable pageable);

    long countByExchange_CodeAndActiveTrue(String exchangeCode);

    @EntityGraph(attributePaths = "exchange")
    @Query(
            value =
                    """
                    SELECT i FROM InstrumentJpaEntity i
                    WHERE i.active = true
                      AND (LOWER(i.symbol) LIKE :pattern ESCAPE '!' OR LOWER(i.name) LIKE :pattern ESCAPE '!')
                    ORDER BY COALESCE(i.catalogRank, 999999) ASC, i.symbol ASC
                    """,
            countQuery =
                    """
                    SELECT COUNT(i) FROM InstrumentJpaEntity i
                    WHERE i.active = true
                      AND (LOWER(i.symbol) LIKE :pattern ESCAPE '!' OR LOWER(i.name) LIKE :pattern ESCAPE '!')
                    """)
    Page<InstrumentJpaEntity> searchAllActive(
            @Param("pattern") String pattern, Pageable pageable);

    @EntityGraph(attributePaths = "exchange")
    @Query(
            value =
                    """
                    SELECT i FROM InstrumentJpaEntity i
                    WHERE i.active = true
                      AND UPPER(i.exchange.code) = :exchangeCode
                      AND (LOWER(i.symbol) LIKE :pattern ESCAPE '!' OR LOWER(i.name) LIKE :pattern ESCAPE '!')
                    ORDER BY COALESCE(i.catalogRank, 999999) ASC, i.symbol ASC
                    """,
            countQuery =
                    """
                    SELECT COUNT(i) FROM InstrumentJpaEntity i
                    WHERE i.active = true
                      AND UPPER(i.exchange.code) = :exchangeCode
                      AND (LOWER(i.symbol) LIKE :pattern ESCAPE '!' OR LOWER(i.name) LIKE :pattern ESCAPE '!')
                    """)
    Page<InstrumentJpaEntity> searchActiveByExchange(
            @Param("exchangeCode") String exchangeCode,
            @Param("pattern") String pattern,
            Pageable pageable);
}
