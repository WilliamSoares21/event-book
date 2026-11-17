package com.eventbook.backend.cards;

import com.eventbook.backend.users.User;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulatedCardRepository extends JpaRepository<SimulatedCard, UUID> {

    List<SimulatedCard> findByUserOrderByCreatedAtDesc(User user);

    Optional<SimulatedCard> findFirstByUserOrderByCreatedAtDesc(User user);

    boolean existsByUserAndStatus(User user, CardStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SimulatedCard c where c.id = :id")
    Optional<SimulatedCard> findByIdForUpdate(@Param("id") UUID id);
}