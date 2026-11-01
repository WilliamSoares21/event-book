package com.eventbook.backend.events;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.eventbook.backend.security.AuthenticatedUser;

import jakarta.validation.Valid;

import java.util.List;
import lombok.RequiredArgsConstructor; 
import java.util.UUID;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
public class EventController {

  private final EventService eventService;

  // Rota pública liberada pelo SecurityConfig, porque o professor deu a dica de deixar publica 
  @GetMapping
  public ResponseEntity<List<EventResponseDTO>> listEvents() {
      List<EventResponseDTO> events = eventService.findAllActiveEvents();
      return ResponseEntity.ok(events);
  }

  //Rota privada (exige token JWT, porque pra postar tem que tá logado)
  //só cria evento quem é Organizador precisamos criar a logica 
  @PostMapping
  public ResponseEntity<EventResponseDTO> createEvent(@Valid @RequestBody EventRequestDTO dto, @AuthenticationPrincipal AuthenticatedUser authUser) {
      EventResponseDTO newEvent = eventService.createNewEvent(dto, authUser.getId());
      return ResponseEntity.status(HttpStatus.CREATED).body(newEvent);
  }
  // Precisa ser ajustado para seguir as regras de negocio do projeto
  @PatchMapping("/{id}")
  public ResponseEntity<EventResponseDTO> updateEvent(
        @PathVariable UUID id,
        @Valid @RequestBody EventUpdateDTO dto,
        @AuthenticationPrincipal AuthenticatedUser authUser) {
    EventResponseDTO updatedEvent = eventService.updateEvent(id, dto, authUser.id());
    return ResponseEntity.status(HttpStatus.OK).body(updatedEvent); 
    }


  // precisamoa ajustarar para apenas o organizador conseguir publicar evento
  @PostMapping("/{id}/publish")
  public ResponseEntity<Void> publishEvent(@PathVariable UUID id) {
      eventService.publish(id); // Faz as validações da regra 01 e muda status
      return ResponseEntity.status(HttpStatus.NO_CONTENT).build(); // Retorna 204
  }

  @GetMapping("/{id}/occupancy")
  public ResponseEntity<EventOccupancyDTO> getOccupancy( @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser authUser) {
    EventOccupancyDTO dto = eventService.getOccupancy(id);
    return ResponseEntity.ok(dto);
    }

}
