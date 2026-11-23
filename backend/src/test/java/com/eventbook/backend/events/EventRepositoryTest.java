package com.eventbook.backend.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class EventRepositoryTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Use existing PostgreSQL container on port 5433 (already has V10 applied)
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:5433/eventbook");
        registry.add("spring.datasource.username", () -> "eventbook");
        registry.add("spring.datasource.password", () -> "devpass");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("security.jwt.secret", () -> "test-secret-key-for-testing-purposes-only-32chars");
    }

    @Autowired
    private EventRepository eventRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID organizerId;
    private UUID userId;
    private OffsetDateTime baseStartsAt;
    private OffsetDateTime baseEndsAt;

    @BeforeEach
    void setup() {
        // Clean up in correct FK order
        entityManager.createNativeQuery("DELETE FROM checkins").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM payment_transactions").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM simulated_cards").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM tickets").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM orders").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM ticket_lots").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM event_staff").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM events").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users").executeUpdate();

        // Create organizer user
        userId = UUID.randomUUID();
        organizerId = userId;
        entityManager.createNativeQuery("""
            INSERT INTO users (id, name, email, password_hash, role, created_at)
            VALUES (?, 'Test Organizer', 'organizer@test.com', 'hash', 'ORGANIZADOR', NOW())
            """)
            .setParameter(1, userId)
            .executeUpdate();

        // Base dates
        baseStartsAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(10);
        baseEndsAt = baseStartsAt.plusHours(4);
    }

    private UUID insertEvent(String title, String city, String state, String venueName,
                             OffsetDateTime startsAt, OffsetDateTime endsAt, EventStatus status) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
            INSERT INTO events (id, organizer_id, title, description, starts_at, ends_at,
                                location, venue_name, street, number, complement,
                                neighborhood, city, state, zip_code, status, created_at, version)
            VALUES (?, ?, ?, 'Description', ?, ?, 'Location', ?, 'Street', '123', 'Ap 1',
                    'Neighborhood', ?, ?, '60000-000', ?, NOW(), 0)
            """)
            .setParameter(1, id)
            .setParameter(2, organizerId)
            .setParameter(3, title)
            .setParameter(4, startsAt)
            .setParameter(5, endsAt)
            .setParameter(6, venueName)
            .setParameter(7, city)
            .setParameter(8, state)
            .setParameter(9, status.name())
            .executeUpdate();
        return id;
    }

    private UUID insertLegacyEvent(String title, String location, OffsetDateTime startsAt, OffsetDateTime endsAt, EventStatus status) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
            INSERT INTO events (id, organizer_id, title, description, starts_at, ends_at,
                                location, status, created_at, version)
            VALUES (?, ?, ?, 'Legacy event', ?, ?, ?, ?, NOW(), 0)
            """)
            .setParameter(1, id)
            .setParameter(2, organizerId)
            .setParameter(3, title)
            .setParameter(4, startsAt)
            .setParameter(5, endsAt)
            .setParameter(6, location)
            .setParameter(7, status.name())
            .executeUpdate();
        return id;
    }

    @Test
    void filterByCity_caseInsensitive_excludesLegacy() {
        // Novo evento com cidade "Fortaleza" (capitalizado)
        insertEvent("Evento Fortaleza", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        // Legado sem cidade
        insertLegacyEvent("Evento Legado", "Fortaleza - CE", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);

        // Busca com lowercase
        Page<Event> page = eventRepository.findPublishedWithFilters(
                null, "fortaleza", null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getCity()).isEqualTo("Fortaleza");
        assertThat(page.getContent().get(0).getState()).isEqualTo("CE");
    }

    @Test
    void filterByCity_differentCapitalizations_findsCorrectly() {
        insertEvent("Evento 1", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertEvent("Evento 2", "SAO PAULO", "SP", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertEvent("Evento 3", "rio de janeiro", "RJ", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);

        // Busca case-insensitive
        Page<Event> page1 = eventRepository.findPublishedWithFilters(
                null, "FORTALEZA", null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );
        assertThat(page1.getTotalElements()).isEqualTo(1);
        assertThat(page1.getContent().get(0).getCity()).isEqualTo("Fortaleza");

        Page<Event> page2 = eventRepository.findPublishedWithFilters(
                null, "sao paulo", null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );
        assertThat(page2.getTotalElements()).isEqualTo(1);
        assertThat(page2.getContent().get(0).getCity()).isEqualTo("SAO PAULO");

        Page<Event> page3 = eventRepository.findPublishedWithFilters(
                null, "Rio De Janeiro", null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );
        assertThat(page3.getTotalElements()).isEqualTo(1);
        assertThat(page3.getContent().get(0).getCity()).isEqualTo("rio de janeiro");
    }

    @Test
    void filterByState_excludesLegacy() {
        insertEvent("Evento CE", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertEvent("Evento SP", "Sao Paulo", "SP", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertLegacyEvent("Evento Legado", "Rio - RJ", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);

        Page<Event> page = eventRepository.findPublishedWithFilters(
                null, null, "CE", null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getState()).isEqualTo("CE");
    }

    @Test
    void filterByDateRange_inclusiveStartExclusiveEnd() {
        // Evento no dia 1
        OffsetDateTime day1Start = baseStartsAt;
        OffsetDateTime day1End = baseStartsAt.plusHours(4);
        insertEvent("Evento Dia 1", "Fortaleza", "CE", "Arena", day1Start, day1End, EventStatus.PUBLISHED);

        // Evento no dia 2
        OffsetDateTime day2Start = baseStartsAt.plusDays(1);
        OffsetDateTime day2End = day2Start.plusHours(4);
        insertEvent("Evento Dia 2", "Fortaleza", "CE", "Arena", day2Start, day2End, EventStatus.PUBLISHED);

        // Evento no dia 3
        OffsetDateTime day3Start = baseStartsAt.plusDays(2);
        OffsetDateTime day3End = day3Start.plusHours(4);
        insertEvent("Evento Dia 3", "Fortaleza", "CE", "Arena", day3Start, day3End, EventStatus.PUBLISHED);

        // Filtro: apenas dia 2 (início do dia 2 até início do dia 3)
        OffsetDateTime startDateTime = day2Start.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endDateTimeExclusive = day2Start.toLocalDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        Page<Event> page = eventRepository.findPublishedWithFilters(
                null, null, null, startDateTime, endDateTimeExclusive,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Evento Dia 2");
    }

    @Test
    void filterByDateRange_boundaryInclusive() {
        // Evento exatamente no startDateTime
        OffsetDateTime exactStart = baseStartsAt.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime exactEnd = exactStart.plusHours(4);
        insertEvent("Evento Início Exato", "Fortaleza", "CE", "Arena", exactStart, exactEnd, EventStatus.PUBLISHED);

        Page<Event> page = eventRepository.findPublishedWithFilters(
                null, null, null, exactStart, exactStart.plusDays(1),
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Evento Início Exato");
    }

    @Test
    void filterByDateRange_boundaryExclusive() {
        // Evento exatamente no endDateTimeExclusive (não deve ser incluído)
        OffsetDateTime endExclusive = baseStartsAt.toLocalDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime exactEndEventStart = endExclusive;
        OffsetDateTime exactEndEventEnd = endExclusive.plusHours(4);
        insertEvent("Evento Fim Exato", "Fortaleza", "CE", "Arena", exactEndEventStart, exactEndEventEnd, EventStatus.PUBLISHED);

        Page<Event> page = eventRepository.findPublishedWithFilters(
                null, null, null, baseStartsAt.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC), endExclusive,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    @Test
    void filterByText_searchCaseInsensitiveInTitleDescriptionVenue() {
        insertEvent("Rock Festival", "Fortaleza", "CE", "Arena Rock", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertEvent("Jazz Night", "Fortaleza", "CE", "Blue Note", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertEvent("Classic Rock Show", "Fortaleza", "CE", "Theater", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);

        // Busca "rock" (case-insensitive)
        Page<Event> page = eventRepository.findPublishedWithFilters(
                "rock", null, null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Event::getTitle)
                .containsExactlyInAnyOrder("Rock Festival", "Classic Rock Show");
    }

    @Test
    void filterByText_withSpecialCharacters_escapedCorrectly() {
        insertEvent("Rock%Festival", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertEvent("Rock_Festival", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertEvent("Rock Festival", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);

        // Busca literal "Rock%Festival" (o % deve ser escapado no Service, aqui testamos com % literal no parâmetro)
        // O parâmetro já deve vir sanitizado do Service, mas testamos o comportamento do ESCAPE '\'
        Page<Event> page = eventRepository.findPublishedWithFilters(
                "Rock\\%Festival", null, null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Rock%Festival");

        // Busca "Rock_Festival" (o _ deve ser escapado)
        Page<Event> page2 = eventRepository.findPublishedWithFilters(
                "Rock\\_Festival", null, null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page2.getTotalElements()).isEqualTo(1);
        assertThat(page2.getContent().get(0).getTitle()).isEqualTo("Rock_Festival");
    }

    @Test
    void filterByText_withBackslash_escapedCorrectly() {
        // Persiste evento com barra invertida literal no título
        insertEvent("Rock\\Festival", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertEvent("Rock Festival", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);

        // Java: "Rock\\Festival" -> string em memória: Rock\Festival
        // Parâmetro q recebido pelo Repository: "Rock\\Festival" (Service escapou a barra para \\)
        // Padrão SQL LIKE com ESCAPE '\\': '%Rock\\Festival%' -> busca literal da barra
        Page<Event> page = eventRepository.findPublishedWithFilters(
                "Rock\\\\Festival", null, null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Rock\\Festival");

        // Confirma que não transforma a barra em escape indevido de caracteres seguintes
        // Busca "Rock\F" não deve matchar "Rock Festival" (espaço != \)
        Page<Event> page2 = eventRepository.findPublishedWithFilters(
                "Rock\\\\F", null, null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );
        assertThat(page2.getTotalElements()).isEqualTo(1);
        assertThat(page2.getContent().get(0).getTitle()).isEqualTo("Rock\\Festival");
    }

    @Test
    void filterByText_emptyOrNull_returnsAllPublished() {
        insertEvent("Evento 1", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertEvent("Evento 2", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);

        // q = null
        Page<Event> page1 = eventRepository.findPublishedWithFilters(
                null, null, null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );
        assertThat(page1.getTotalElements()).isEqualTo(2);

        // q = ""
        Page<Event> page2 = eventRepository.findPublishedWithFilters(
                "", null, null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );
        assertThat(page2.getTotalElements()).isEqualTo(2);
    }

    @Test
    void filterByMultipleFilters_ANDLogic() {
        // Evento que corresponde a todos os filtros
        insertEvent("Rock Fortaleza", "Fortaleza", "CE", "Arena Rock", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        // Evento que corresponde apenas a cidade
        insertEvent("Jazz Fortaleza", "Fortaleza", "CE", "Blue Note", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        // Evento que corresponde apenas a estado
        insertEvent("Rock Sao Paulo", "Sao Paulo", "SP", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        // Evento que corresponde apenas a texto
        insertEvent("Rock Rio", "Rio de Janeiro", "RJ", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        // Evento que corresponde apenas a data
        OffsetDateTime otherDate = baseStartsAt.plusDays(5);
        insertEvent("Outro", "Outra Cidade", "SP", "Arena", otherDate, otherDate.plusHours(4), EventStatus.PUBLISHED);

        // Combinação: q=rock + city=fortaleza + state=CE + data range
        Page<Event> page = eventRepository.findPublishedWithFilters(
                "rock", "fortaleza", "CE",
                baseStartsAt.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC),
                baseStartsAt.toLocalDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC),
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Rock Fortaleza");
    }

    @Test
    void filterExcludesDraftEvents() {
        insertEvent("Evento Publicado", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertEvent("Evento Rascunho", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.DRAFT);
        insertEvent("Evento Cancelado", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.CANCELLED);
        insertEvent("Evento Finalizado", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.FINISHED);

        Page<Event> page = eventRepository.findPublishedWithFilters(
                null, null, null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Evento Publicado");
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void filterByCityAndState_caseInsensitiveAndNormalized() {
        insertEvent("Evento 1", "Fortaleza", "CE", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);
        insertEvent("Evento 2", "SAO PAULO", "SP", "Arena", baseStartsAt, baseEndsAt, EventStatus.PUBLISHED);

        // city lowercase, state uppercase (normalizado pelo Service)
        Page<Event> page = eventRepository.findPublishedWithFilters(
                null, "fortaleza", "CE", null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getCity()).isEqualTo("Fortaleza");
        assertThat(page.getContent().get(0).getState()).isEqualTo("CE");
    }

    @Test
    void pagination_totalElementsAndDeterministicOrdering() {
        // Criar 3 eventos com mesma data, títulos diferentes
        OffsetDateTime sameStart = baseStartsAt;
        OffsetDateTime sameEnd = sameStart.plusHours(4);

        insertEvent("C Evento", "Fortaleza", "CE", "Arena", sameStart, sameEnd, EventStatus.PUBLISHED);
        insertEvent("A Evento", "Fortaleza", "CE", "Arena", sameStart, sameEnd, EventStatus.PUBLISHED);
        insertEvent("B Evento", "Fortaleza", "CE", "Arena", sameStart, sameEnd, EventStatus.PUBLISHED);

        // Paginação: página 0, tamanho 2
        Page<Event> page0 = eventRepository.findPublishedWithFilters(
                null, null, null, null, null,
                PageRequest.of(0, 2, Sort.by("title").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        assertThat(page0.getContent()).hasSize(2);
        // Ordenação determinística: title ASC + id ASC
        assertThat(page0.getContent().get(0).getTitle()).isEqualTo("A Evento");
        assertThat(page0.getContent().get(1).getTitle()).isEqualTo("B Evento");

        // Página 1
        Page<Event> page1 = eventRepository.findPublishedWithFilters(
                null, null, null, null, null,
                PageRequest.of(1, 2, Sort.by("title").ascending().and(Sort.by("id").ascending()))
        );

        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.getContent().get(0).getTitle()).isEqualTo("C Evento");
    }

    @Test
    void sortByDifferentFields_deterministicWithIdTieBreaker() {
        OffsetDateTime sameStart = baseStartsAt;
        OffsetDateTime sameEnd = sameStart.plusHours(4);

        // UUIDs controlados explicitamente para tie-breaker determinístico
        // id1 < id2 lexicograficamente (000... < fff...)
        UUID id1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID id2 = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        // Inserir com UUIDs controlados (ordem de inserção: Z primeiro, depois A)
        insertEventWithId(id1, "Evento Z", "Fortaleza", "CE", "Arena", sameStart, sameEnd, EventStatus.PUBLISHED);
        insertEventWithId(id2, "Evento A", "Fortaleza", "CE", "Arena", sameStart, sameEnd, EventStatus.PUBLISHED);

        // Sort por title ASC
        Page<Event> byTitle = eventRepository.findPublishedWithFilters(
                null, null, null, null, null,
                PageRequest.of(0, 10, Sort.by("title").ascending().and(Sort.by("id").ascending()))
        );
        assertThat(byTitle.getContent().get(0).getTitle()).isEqualTo("Evento A");
        assertThat(byTitle.getContent().get(1).getTitle()).isEqualTo("Evento Z");

        // Sort por title DESC
        Page<Event> byTitleDesc = eventRepository.findPublishedWithFilters(
                null, null, null, null, null,
                PageRequest.of(0, 10, Sort.by("title").descending().and(Sort.by("id").ascending()))
        );
        assertThat(byTitleDesc.getContent().get(0).getTitle()).isEqualTo("Evento Z");
        assertThat(byTitleDesc.getContent().get(1).getTitle()).isEqualTo("Evento A");

        // Sort por startsAt ASC (mesma data) - tie-breaker id ASC
        // id1 (AAA...) < id2 (ZZZ...), então id1 deve vir primeiro
        Page<Event> byStartsAt = eventRepository.findPublishedWithFilters(
                null, null, null, null, null,
                PageRequest.of(0, 10, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()))
        );
        assertThat(byStartsAt.getContent().get(0).getId()).isEqualTo(id1);
        assertThat(byStartsAt.getContent().get(1).getId()).isEqualTo(id2);
    }

    private void insertEventWithId(UUID id, String title, String city, String state, String venueName,
                                    OffsetDateTime startsAt, OffsetDateTime endsAt, EventStatus status) {
        entityManager.createNativeQuery("""
            INSERT INTO events (id, organizer_id, title, description, starts_at, ends_at,
                                location, venue_name, street, number, complement,
                                neighborhood, city, state, zip_code, status, created_at, version)
            VALUES (?, ?, ?, 'Description', ?, ?, 'Location', ?, 'Street', '123', 'Ap 1',
                    'Neighborhood', ?, ?, '60000-000', ?, NOW(), 0)
            """)
            .setParameter(1, id)
            .setParameter(2, organizerId)
            .setParameter(3, title)
            .setParameter(4, startsAt)
            .setParameter(5, endsAt)
            .setParameter(6, venueName)
            .setParameter(7, city)
            .setParameter(8, state)
            .setParameter(9, status.name())
            .executeUpdate();
    }
}