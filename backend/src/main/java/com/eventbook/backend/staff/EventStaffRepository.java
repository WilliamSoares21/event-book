package com.eventbook.backend.staff;

import com.eventbook.backend.staff.dto.StaffEventResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface EventStaffRepository extends JpaRepository <EventStaff, UUID> {

    @Query("""
            select new com.eventbook.backend.staff.dto.StaffEventResponse(
                event.id, event.title, event.startsAt, event.location)
            from EventStaff staff join staff.event event
            where staff.user.id = :userId
            order by event.startsAt, event.id
            """)
    List<StaffEventResponse> findEventsByUserId(@Param("userId") UUID userId);

    @Query("select staff.user.id from EventStaff staff where staff.event.id = :eventId")
    Set<UUID> findUserIdsByEventId(@Param("eventId") UUID eventId);

    // CORREÇÃO: Adicionado o "And" para separar as propriedades de busca
    Optional<EventStaff> findByEventIdAndUserId(UUID eventId, UUID userId);

    // Este já estava correto!
    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);
}
