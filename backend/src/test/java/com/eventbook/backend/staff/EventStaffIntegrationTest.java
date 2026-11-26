package com.eventbook.backend.staff;

import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.users.Role;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class EventStaffIntegrationTest {

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
        registry.add("security.jwt.secret", () -> "9843575ce52ce3295ae06df9de4fd1f64f4c55243a02da9efc2c102c8555343e");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketLotRepository ticketLotRepository;

    @Autowired
    private EventStaffRepository eventStaffRepository;

    private String commonPassword = "password123";

    @BeforeEach
    void setup() {
        eventStaffRepository.deleteAll();
        ticketLotRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createOrganizer() {
        User organizer = User.builder()
                .name("Test Organizer")
                .email("organizer@test.com")
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.ORGANIZADOR)
                .build();
        return userRepository.save(organizer);
    }

    private User createStaff() {
        User staff = User.builder()
                .name("Staff User")
                .email("staff@test.com")
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.STAFF)
                .build();
        return userRepository.save(staff);
    }

    private User createBuyer() {
        User buyer = User.builder()
                .name("Buyer User")
                .email("buyer@test.com")
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.COMPRADOR)
                .build();
        return userRepository.save(buyer);
    }

    private Event createPublishedEvent(User organizer) {
        Event event = Event.builder()
                .title("Test Event")
                .description("Test Description")
                .startsAt(OffsetDateTime.now().plus(10, ChronoUnit.DAYS))
                .endsAt(OffsetDateTime.now().plus(10, ChronoUnit.DAYS).plusHours(2))
                .location("Test Location")
                .organizer(organizer)
                .status(EventStatus.PUBLISHED)
                .build();
        return eventRepository.save(event);
    }

    private String obtainToken(String email) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email": "%s",
                                "password": "%s"
                            }
                            """.formatted(email, commonPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        // Extract token from JSON response
        return responseBody.split("\"token\":\"")[1].split("\"")[0];
    }

    @Test
    void organizadorProprietarioDeveAssociarStaffComSucesso() throws Exception {
        User organizer = createOrganizer();
        User staff = createStaff();
        Event event = createPublishedEvent(organizer);

        String token = obtainToken(organizer.getEmail());

        mockMvc.perform(post("/api/events/{eventId}/staff", event.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staff.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.userId").value(staff.getId().toString()))
                .andExpect(jsonPath("$.userName").value(staff.getName()))
                .andExpect(jsonPath("$.userEmail").value(staff.getEmail()));
    }

    @Test
    void deveRetornar404QuandoEventoNaoExiste() throws Exception {
        User organizer = createOrganizer();
        User staff = createStaff();

        String token = obtainToken(organizer.getEmail());

        UUID nonExistentEventId = UUID.randomUUID();

        mockMvc.perform(post("/api/events/{eventId}/staff", nonExistentEventId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staff.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Evento não encontrado"));
    }

    @Test
    void deveRetornar404QuandoUsuarioNaoExiste() throws Exception {
        User organizer = createOrganizer();
        Event event = createPublishedEvent(organizer);

        String token = obtainToken(organizer.getEmail());

        UUID nonExistentUserId = UUID.randomUUID();

        mockMvc.perform(post("/api/events/{eventId}/staff", event.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(nonExistentUserId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Usuário não encontrado"));
    }

    @Test
    void deveRetornar422QuandoUsuarioNaoTemRoleStaff() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer();
        Event event = createPublishedEvent(organizer);

        String token = obtainToken(organizer.getEmail());

        mockMvc.perform(post("/api/events/{eventId}/staff", event.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(buyer.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("O usuário indicado deve possuir role STAFF"));
    }

    @Test
    void deveRetornar403QuandoOrganizadorNaoEProprietario() throws Exception {
        User organizer1 = createOrganizer();
        User organizer2 = User.builder()
                .name("Another Organizer")
                .email("organizer2@test.com")
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.ORGANIZADOR)
                .build();
        organizer2 = userRepository.save(organizer2);

        User staff = createStaff();
        Event event = createPublishedEvent(organizer1); // Event belongs to organizer1

        String token = obtainToken(organizer2.getEmail()); // But organizer2 tries to add staff

        mockMvc.perform(post("/api/events/{eventId}/staff", event.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staff.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Somente o organizador proprietário pode associar staff ao evento"));
    }

    @Test
    void deveRetornar403QuandoUsuarioNaoEOrganizador() throws Exception {
        User organizer = createOrganizer();
        User staff = createStaff();
        Event event = createPublishedEvent(organizer);

        String token = obtainToken(staff.getEmail()); // Staff tries to add staff

        mockMvc.perform(post("/api/events/{eventId}/staff", event.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staff.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRetornar401QuandoSemToken() throws Exception {
        User organizer = createOrganizer();
        User staff = createStaff();
        Event event = createPublishedEvent(organizer);

        mockMvc.perform(post("/api/events/{eventId}/staff", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staff.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar409QuandoAssociacaoDuplicada() throws Exception {
        User organizer = createOrganizer();
        User staff = createStaff();
        Event event = createPublishedEvent(organizer);

        String token = obtainToken(organizer.getEmail());

        // First association - should succeed
        mockMvc.perform(post("/api/events/{eventId}/staff", event.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staff.getId())))
                .andExpect(status().isCreated());

        // Second association - should fail with 409
        mockMvc.perform(post("/api/events/{eventId}/staff", event.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staff.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Usuário já associado a este evento"));
    }

    @Test
    void deveRetornar400QuandoPayloadInvalido() throws Exception {
        User organizer = createOrganizer();
        Event event = createPublishedEvent(organizer);

        String token = obtainToken(organizer.getEmail());

        mockMvc.perform(post("/api/events/{eventId}/staff", event.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fields.userId").exists());
    }

    @Test
    void deveRetornar400QuandoUserIdNaoEUuid() throws Exception {
        User organizer = createOrganizer();
        Event event = createPublishedEvent(organizer);

        String token = obtainToken(organizer.getEmail());

        mockMvc.perform(post("/api/events/{eventId}/staff", event.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "not-a-uuid"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}