package com.eventbook.backend.lots;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketLotRepository extends JpaRepository <TicketLot, UUID> {

    List<TicketLot> findByEventId(UUID eventId);

    boolean existsByEventIdAndStatus(UUID eventId, LotStatus status);

    @Query("""
            SELECT COALESCE(SUM(t.capacity), 0)
            FROM TicketLot t
            WHERE t.event.id = :eventId
            """)
    Long sumCapacityByEvent(@Param("eventId") UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketLot t where t.id = :id")
    Optional<TicketLot> findByIdForUpdate(@Param("id") UUID id);
}
