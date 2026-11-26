package com.eventbook.backend.events;

import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.users.Role;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catálogo público de compra: filtra no banco (antes da paginação) eventos
 * publicados, não encerrados e com ao menos um lote vendável.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventCatalogIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("eventbook")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> "9843575ce52ce3295ae06df9de4fd1f64f4c55243a02da9efc2c102c8555343e");
        registry.add("r2.endpoint", () -> "https://dummy.r2.cloudflarestorage.com");
        registry.add("r2.access-key", () -> "dummy");
        registry.add("r2.secret-key", () -> "dummy");
        registry.add("r2.bucket", () -> "dummy-bucket");
        registry.add("r2.public-base-url", () -> "https://dummy.r2.cloudflarestorage.com");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketLotRepository ticketLotRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MockMvc mockMvc;
    @PersistenceContext
    private EntityManager entityManager;

    private User organizer;
    private Instant now;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("DELETE FROM ticket_lots").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM events").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users").executeUpdate();

        now = Instant.now();
        organizer = userRepository.save(User.builder()
                .name("Organizador")
                .email("organizador-" + UUID.randomUUID() + "@test.com")
                .passwordHash("hash")
                .role(Role.ORGANIZADOR)
                .build());
    }

    private Event publish(String title, String location, OffsetDateTime startsAt, OffsetDateTime endsAt) {
        return eventRepository.save(Event.builder()
                .organizer(organizer)
                .title(title)
                .description("Evento de teste")
                .startsAt(startsAt)
                .endsAt(endsAt)
                .location(location)
                .status(EventStatus.PUBLISHED)
                .build());
    }

    private void lot(Event event, LotStatus status, Instant salesStart, Instant salesEnd, int capacity, int sold) {
        ticketLotRepository.save(TicketLot.builder()
                .event(event)
                .name("Lote " + UUID.randomUUID())
                .unitPrice(BigDecimal.TEN)
                .capacity(capacity)
                .sold(sold)
                .reserved(0)
                .salesStart(salesStart)
                .salesEnd(salesEnd)
                .status(status)
                .build());
    }

    private Page<Event> catalog(int page, int size, String q) {
        OffsetDateTime reference = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        return eventRepository.findPublishedWithFilters(
                q, null, null, null, null, true, reference, now,
                PageRequest.of(page, size, Sort.by("startsAt").ascending().and(Sort.by("id").ascending())));
    }

    @Test
    void listaSomenteEventosPublicadosNaoEncerradosComLoteVendavel() {
        OffsetDateTime futureStart = OffsetDateTime.ofInstant(now.plusSeconds(3600), ZoneOffset.UTC);
        OffsetDateTime futureEnd = futureStart.plusHours(4);

        // Evento com lote à venda: deve aparecer.
        Event compravel = publish("Conferência Tech", "Fortaleza - CE", futureStart, futureEnd);
        lot(compravel, LotStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 2);

        // Evento já encerrado: não pode aparecer.
        Event encerrado = publish("Java Nautas", "Fortaleza - CE",
                futureStart.minusDays(10), futureEnd.minusDays(10));
        lot(encerrado, LotStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 0);

        // Evento sem nenhum lote: não pode aparecer.
        publish("Sem lotes", "Recife - PE", futureStart, futureEnd);

        // Lote com janela de vendas encerrada: não pode aparecer.
        Event loteExpirado = publish("Lote expirado", "Salvador - BA", futureStart, futureEnd);
        lot(loteExpirado, LotStatus.ACTIVE, now.minusSeconds(7200), now.minusSeconds(3600), 10, 0);

        // Lote inativo: não pode aparecer.
        Event loteInativo = publish("Lote inativo", "Natal - RN", futureStart, futureEnd);
        lot(loteInativo, LotStatus.INACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 0);

        // Lote esgotado: não pode aparecer.
        Event esgotado = publish("Lote esgotado", "João Pessoa - PB", futureStart, futureEnd);
        lot(esgotado, LotStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 10);

        // Evento em andamento (começou, mas não terminou): deve aparecer.
        Event emAndamento = publish("Em andamento", "Maceió - AL",
                OffsetDateTime.ofInstant(now.minusSeconds(1800), ZoneOffset.UTC), futureEnd);
        lot(emAndamento, LotStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 0);

        Page<Event> page = catalog(0, 10, null);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(Event::getTitle)
                .containsExactlyInAnyOrder("Conferência Tech", "Em andamento");
    }

    @Test
    void buscaPorTerminoConsideraTituloELocalizacaoComPaginaçãoCoerente() {
        OffsetDateTime futureStart = OffsetDateTime.ofInstant(now.plusSeconds(3600), ZoneOffset.UTC);
        OffsetDateTime futureEnd = futureStart.plusHours(4);

        for (int i = 0; i < 12; i++) {
            Event evento = publish("Palestra Tech " + i, "Fortaleza - CE", futureStart.plusDays(i), futureEnd.plusDays(i));
            lot(evento, LotStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 0);
        }
        Event foraDoTermo = publish("Show de Música", "Recife - PE", futureStart, futureEnd);
        lot(foraDoTermo, LotStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 0);

        Page<Event> primeiraPagina = catalog(0, 5, "tech");

        assertThat(primeiraPagina.getTotalElements()).isEqualTo(12);
        assertThat(primeiraPagina.getTotalPages()).isEqualTo(3);
        assertThat(primeiraPagina.getContent()).hasSize(5);

        Page<Event> ultimaPagina = catalog(2, 5, "tech");

        assertThat(ultimaPagina.getContent()).hasSize(2);

        // A localização também é pesquisável.
        assertThat(catalog(0, 10, "recife").getTotalElements()).isEqualTo(1);
    }

    @Test
    void endpointAvailableRetornaSomenteEventosCompraveis() throws Exception {
        OffsetDateTime futureStart = OffsetDateTime.ofInstant(now.plusSeconds(3600), ZoneOffset.UTC);
        OffsetDateTime futureEnd = futureStart.plusHours(4);

        Event compravel = publish("Conferência Tech", "Fortaleza - CE", futureStart, futureEnd);
        lot(compravel, LotStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 1);

        Event encerrado = publish("Evento encerrado", "Fortaleza - CE",
                futureStart.minusDays(10), futureEnd.minusDays(10));
        lot(encerrado, LotStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 0);

        mockMvc.perform(get("/api/events/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Conferência Tech"));

        mockMvc.perform(get("/api/events/available").param("q", "tech"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/events/available").param("q", "inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listaDeEventosPublicadosMantemComportamentoOriginal() throws Exception {
        OffsetDateTime futureStart = OffsetDateTime.ofInstant(now.plusSeconds(3600), ZoneOffset.UTC);
        OffsetDateTime futureEnd = futureStart.plusHours(4);

        // Evento encerrado e sem lote continua visível em GET /api/events.
        publish("Evento encerrado", "Fortaleza - CE", futureStart.minusDays(10), futureEnd.minusDays(10));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void termoComCuringasDoLikeEhPesquisadoLiteralmente() {
        OffsetDateTime futureStart = OffsetDateTime.ofInstant(now.plusSeconds(3600), ZoneOffset.UTC);
        OffsetDateTime futureEnd = futureStart.plusHours(4);

        Event comCuringa = publish("Promoção 50% OFF", "Fortaleza - CE", futureStart, futureEnd);
        lot(comCuringa, LotStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 0);

        Event semCuringa = publish("Show de Música", "Recife - PE", futureStart, futureEnd);
        lot(semCuringa, LotStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 0);

        // O repositório recebe o termo já escapado pelo serviço (mesmo contrato do EventRepositoryTest).
        assertThat(catalog(0, 10, "50\\%").getTotalElements()).isEqualTo(1);

        // "%" escapado casa apenas com o título que contém a curinga literal.
        Page<Event> porCuringa = catalog(0, 10, "\\%");

        assertThat(porCuringa.getTotalElements()).isEqualTo(1);
        assertThat(porCuringa.getContent().get(0).getTitle()).isEqualTo("Promoção 50% OFF");
    }

    @Test
    void buscaPorCidadeMantemCorrespondenciaExata() {
        OffsetDateTime futureStart = OffsetDateTime.ofInstant(now.plusSeconds(3600), ZoneOffset.UTC);
        OffsetDateTime futureEnd = futureStart.plusHours(4);

        Event evento = publish("Evento em Fortaleza", "Fortaleza - CE", futureStart, futureEnd);
        evento.setCity("Fortaleza");
        evento.setState("CE");
        eventRepository.save(evento);
        lot(evento, LotStatus.ACTIVE, now.minusSeconds(60), now.plusSeconds(3600), 10, 0);

        OffsetDateTime reference = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        Page<Event> porCidade = eventRepository.findPublishedWithFilters(
                null, "fortaleza", null, null, null, true, reference, now,
                PageRequest.of(0, 10));

        assertThat(porCidade.getTotalElements()).isEqualTo(1);
        assertThat(List.of(porCidade.getContent().get(0).getTitle())).containsExactly("Evento em Fortaleza");
    }
}
